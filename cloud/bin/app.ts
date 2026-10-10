import {App} from 'aws-cdk-lib';
import {DataStack,ServiceStack,BudgetStack} from '../lib/stacks';
const app = new App();
const stage = app.node.tryGetContext('stage');
if (stage !== 'dev' && stage !== 'prod') throw new Error('stage 必须是 dev 或 prod');
const env = {account:process.env.CDK_DEFAULT_ACCOUNT,region:app.node.tryGetContext('region') ?? 'ap-east-1'};
const storage = new DataStack(app,`PersonalCalendar-${stage}-Data`,{stage,env});
new ServiceStack(app,`PersonalCalendar-${stage}-Service`,{stage,env,storage});
if (stage === 'prod') new BudgetStack(app,'PersonalCalendar-prod-Budget',{
  env:{account:env.account,region:'us-east-1'},alertEmail:process.env.ALERT_EMAIL});
