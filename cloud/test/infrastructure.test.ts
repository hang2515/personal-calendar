import {test} from 'node:test';
import {App} from 'aws-cdk-lib';
import {Template,Match} from 'aws-cdk-lib/assertions';
import {DataStack,ServiceStack} from '../lib/stacks';
test('backup resources require private storage, token scopes and conditional-capable write permissions',()=>{
  const app = new App({context:{'@aws-cdk/core:defaultCrossStackReferences':'strong'}});
  const storage = new DataStack(app,'TestData',{stage:'dev'});
  const service = new ServiceStack(app,'TestService',{stage:'dev',storage});
  const data = Template.fromStack(storage), api = Template.fromStack(service);
  data.hasResourceProperties('AWS::S3::Bucket',{VersioningConfiguration:{Status:'Enabled'},
    PublicAccessBlockConfiguration:{BlockPublicAcls:true,BlockPublicPolicy:true,IgnorePublicAcls:true,RestrictPublicBuckets:true}});
  data.hasResourceProperties('AWS::Cognito::UserPoolClient',{GenerateSecret:false,AllowedOAuthFlows:['code'],EnableTokenRevocation:true});
  data.hasResourceProperties('AWS::Cognito::UserPoolDomain',{ManagedLoginVersion:2});
  data.hasResourceProperties('AWS::Cognito::ManagedLoginBranding',{
    UserPoolId:{Ref:Match.anyValue()},ClientId:{Ref:Match.anyValue()},UseCognitoProvidedValues:true,
  });
  api.allResourcesProperties('AWS::ApiGatewayV2::Route',{AuthorizationType:'JWT',AuthorizationScopes:['personal-calendar/backup']});
  api.hasResourceProperties('AWS::Lambda::Function',{VpcConfig:Match.absent()});
});
