import { Stack, StackProps, Duration, RemovalPolicy, CfnOutput, Tags } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as node from 'aws-cdk-lib/aws-lambda-nodejs';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as api from 'aws-cdk-lib/aws-apigatewayv2';
import * as auth from 'aws-cdk-lib/aws-apigatewayv2-authorizers';
import * as integration from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as budgets from 'aws-cdk-lib/aws-budgets';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as path from 'node:path';

type StageProps = StackProps & { readonly stage: 'dev' | 'prod' };
export class DataStack extends Stack {
  readonly bucket: s3.Bucket;
  readonly pool: cognito.UserPool;
  readonly client: cognito.UserPoolClient;
  readonly domain: cognito.UserPoolDomain;
  constructor(scope: Construct, id: string, props: StageProps) {
    super(scope, id, {...props, terminationProtection:true});
    Tags.of(this).add('Project','personal-calendar'); Tags.of(this).add('Stage',props.stage);
    this.bucket = new s3.Bucket(this, 'Backups', {encryption:s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess:s3.BlockPublicAccess.BLOCK_ALL, enforceSSL:true, versioned:true,
      objectOwnership:s3.ObjectOwnership.BUCKET_OWNER_ENFORCED, removalPolicy:RemovalPolicy.RETAIN,
      lifecycleRules:[{noncurrentVersionExpiration:Duration.days(30), abortIncompleteMultipartUploadAfter:Duration.days(1)}]});
    this.pool = new cognito.UserPool(this,'Accounts',{selfSignUpEnabled:true,
      signInAliases:{email:true}, signInCaseSensitive:false, autoVerify:{email:true},
      standardAttributes:{email:{required:true,mutable:true}}, accountRecovery:cognito.AccountRecovery.EMAIL_ONLY,
      featurePlan:cognito.FeaturePlan.ESSENTIALS, mfa:cognito.Mfa.OPTIONAL,
      mfaSecondFactor:{sms:false,otp:true}, deletionProtection:true, removalPolicy:RemovalPolicy.RETAIN,
      passwordPolicy:{minLength:12,requireDigits:true,requireLowercase:true,requireUppercase:true,requireSymbols:true}});
    const scopeDefinition = new cognito.ResourceServerScope({scopeName:'backup',scopeDescription:'读写本账号日历备份'});
    const resource = this.pool.addResourceServer('BackupScope',{identifier:'personal-calendar', scopes:[scopeDefinition]});
    this.client = this.pool.addClient('Android',{generateSecret:false, preventUserExistenceErrors:true,
      enableTokenRevocation:true, accessTokenValidity:Duration.minutes(15), idTokenValidity:Duration.minutes(15),
      refreshTokenValidity:Duration.days(7), refreshTokenRotationGracePeriod:Duration.seconds(10),
      authFlows:{userSrp:true}, oAuth:{flows:{authorizationCodeGrant:true},
        scopes:[cognito.OAuthScope.OPENID,cognito.OAuthScope.EMAIL,cognito.OAuthScope.resourceServer(resource,scopeDefinition)],
        callbackUrls:['com.hang2515.personalcalendar://oauth/callback'],
        logoutUrls:['com.hang2515.personalcalendar://oauth/logout']}});
    this.domain = this.pool.addDomain('Login',{cognitoDomain:{domainPrefix:`personal-calendar-${props.stage}-${this.account}`},
      managedLoginVersion:cognito.ManagedLoginVersion.CLASSIC_HOSTED_UI});
    new CfnOutput(this,'UserPoolId',{value:this.pool.userPoolId});
    new CfnOutput(this,'ClientId',{value:this.client.userPoolClientId});
    new CfnOutput(this,'AuthDomain',{value:this.domain.baseUrl()});
    new CfnOutput(this,'BackupBucket',{value:this.bucket.bucketName});
  }
}
export class ServiceStack extends Stack {
  constructor(scope: Construct, id: string, props: StageProps & {readonly storage:DataStack}) {
    super(scope,id,props);
    Tags.of(this).add('Project','personal-calendar'); Tags.of(this).add('Stage',props.stage);
    const logGroup = new logs.LogGroup(this,'FunctionLogs',{retention:logs.RetentionDays.ONE_WEEK,removalPolicy:RemovalPolicy.DESTROY});
    const handler = new node.NodejsFunction(this,'BackupHandler',{entry:path.join(__dirname,'../src/handler.ts'),
      runtime:lambda.Runtime.NODEJS_22_X, architecture:lambda.Architecture.ARM_64,
      memorySize:256,timeout:Duration.seconds(15),logGroup,
      bundling:{minify:true,sourceMap:false,externalModules:[]},
      environment:{BACKUP_BUCKET:props.storage.bucket.bucketName}});
    handler.addToRolePolicy(new iam.PolicyStatement({actions:['s3:GetObject','s3:PutObject'],resources:[props.storage.bucket.arnForObjects('backups/*')]}));
    handler.addToRolePolicy(new iam.PolicyStatement({actions:['s3:ListBucket'],resources:[props.storage.bucket.bucketArn]}));
    const authorizer = new auth.HttpJwtAuthorizer('Accounts',props.storage.pool.userPoolProviderUrl,
      {jwtAudience:[props.storage.client.userPoolClientId]});
    const http = new api.HttpApi(this,'BackupApi',{apiName:`personal-calendar-${props.stage}`,
      defaultAuthorizer:authorizer, defaultAuthorizationScopes:['personal-calendar/backup']});
    const endpoint = new integration.HttpLambdaIntegration('BackupIntegration',handler);
    http.addRoutes({path:'/v1/backup',methods:[api.HttpMethod.GET],integration:endpoint});
    http.addRoutes({path:'/v1/backup/snapshot',methods:[api.HttpMethod.GET,api.HttpMethod.PUT],integration:endpoint});
    const accessLogs = new logs.LogGroup(this,'ApiLogs',{retention:logs.RetentionDays.ONE_WEEK,removalPolicy:RemovalPolicy.DESTROY});
    const stage = http.defaultStage!.node.defaultChild as api.CfnStage;
    stage.defaultRouteSettings = {throttlingBurstLimit:10,throttlingRateLimit:5};
    stage.accessLogSettings = {destinationArn:accessLogs.logGroupArn,
      format:JSON.stringify({requestId:'$context.requestId',status:'$context.status',routeKey:'$context.routeKey'})};
    new cloudwatch.Alarm(this,'BackupErrors',{metric:handler.metricErrors({period:Duration.minutes(5)}),
      threshold:3,evaluationPeriods:1,treatMissingData:cloudwatch.TreatMissingData.NOT_BREACHING});
    new CfnOutput(this,'ApiUrl',{value:http.apiEndpoint});
  }
}

// Budgets 使用全局账单数据，在支持该 CloudFormation 资源的美国东部部署。
export class BudgetStack extends Stack {
  constructor(scope: Construct, id: string, props: StackProps & {readonly alertEmail?:string}) {
    super(scope,id,props);
    Tags.of(this).add('Project','personal-calendar');
    if (!props.alertEmail) throw new Error('部署生产环境需要通过 ALERT_EMAIL 设置费用预警邮箱');
    new budgets.CfnBudget(this,'MonthlyBudget',{budget:{budgetName:'personal-calendar-monthly',budgetType:'COST',
      timeUnit:'MONTHLY',budgetLimit:{amount:5,unit:'USD'},costTypes:{includeCredit:false,includeRefund:false}},
      notificationsWithSubscribers:[80,100].map(threshold=>({notification:{notificationType:'ACTUAL',comparisonOperator:'GREATER_THAN',
        threshold,thresholdType:'PERCENTAGE'},subscribers:[{subscriptionType:'EMAIL',address:props.alertEmail!}]}))});
  }
}
