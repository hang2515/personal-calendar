param([string]$Profile='my-aws', [string]$Region='ap-east-1')
$ErrorActionPreference='Stop'
$awsCommon=@('--profile',$Profile,'--region',$Region,'--no-cli-pager')
function Read-AwsJson([string[]]$Arguments) {
    $text = & aws @Arguments @awsCommon
    if ($LASTEXITCODE -ne 0) { throw 'AWS 命令执行失败' }
    return ($text | ConvertFrom-Json)
}
$dataStack=Read-AwsJson @('cloudformation','describe-stacks','--stack-name','PersonalCalendar-dev-Data')
$serviceStack=Read-AwsJson @('cloudformation','describe-stacks','--stack-name','PersonalCalendar-dev-Service')
$bucket=($dataStack.Stacks[0].Outputs | Where-Object OutputKey -EQ 'BackupBucket').OutputValue
$url=($serviceStack.Stacks[0].Outputs | Where-Object OutputKey -EQ 'ApiUrl').OutputValue
$resources=Read-AwsJson @('cloudformation','list-stack-resources','--stack-name','PersonalCalendar-dev-Service')
$function=($resources.StackResourceSummaries | Where-Object ResourceType -EQ 'AWS::Lambda::Function').PhysicalResourceId
$runId='smoke-'+[guid]::NewGuid().ToString()
$directory=Join-Path ([IO.Path]::GetTempPath()) $runId
New-Item -ItemType Directory -Path $directory | Out-Null
$payloadFile=Join-Path $directory 'payload.json'
$responseFile=Join-Path $directory 'response.json'
$key="backups/$runId/snapshot.json"
$snapshot=@{formatVersion=1;timeZone='Asia/Shanghai';backupId=$runId;datasetId=$runId;sourceRevision=0;
    createdAt=[DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ');calendars=@();scheduleChanges=@();checkIns=@()}
function Invoke-Backup([string]$Route,[string]$Owner,[hashtable]$Headers,[string]$Body) {
    $payload=@{routeKey=$Route;headers=$Headers;body=$Body;requestContext=@{requestId=$runId;authorizer=@{jwt=@{claims=@{sub=$Owner;token_use='access'}}}}}
    [IO.File]::WriteAllText($payloadFile,($payload | ConvertTo-Json -Depth 10 -Compress),[Text.UTF8Encoding]::new($false))
    & aws lambda invoke --function-name $function --payload "fileb://$payloadFile" $responseFile @awsCommon | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Lambda 调用失败' }
    return (Get-Content -LiteralPath $responseFile -Raw | ConvertFrom-Json)
}
try {
    $response=Invoke-WebRequest -Uri "$url/v1/backup" -SkipHttpErrorCheck
    if ([int]$response.StatusCode -ne 401) { throw '未登录请求未被拒绝' }
    $response=Invoke-WebRequest -Uri "$url/v1/backup" -Headers @{Authorization='Bearer invalid'} -SkipHttpErrorCheck
    if ([int]$response.StatusCode -ne 401) { throw '无效令牌未被拒绝' }
    $body=$snapshot | ConvertTo-Json -Depth 10 -Compress
    $first=Invoke-Backup 'PUT /v1/backup/snapshot' $runId @{'if-none-match'='*'} $body
    if ($first.statusCode -ne 200) { throw "首次备份失败：$($first.body)" }
    $retry=Invoke-Backup 'PUT /v1/backup/snapshot' $runId @{'if-none-match'='*'} $body
    if ($retry.statusCode -ne 200) { throw '重复上传未保持幂等' }
    $other=Invoke-Backup 'GET /v1/backup' ($runId+'-other') @{} ''
    if ($other.statusCode -ne 404) { throw '账号隔离失败' }
    $snapshot.backupId=$runId+'-new'
    $changed=$snapshot | ConvertTo-Json -Depth 10 -Compress
    $conflict=Invoke-Backup 'PUT /v1/backup/snapshot' $runId @{'if-none-match'='*'} $changed
    if ($conflict.statusCode -ne 409) { throw '旧版本覆盖未被拒绝' }
    $updated=Invoke-Backup 'PUT /v1/backup/snapshot' $runId @{'if-match'=$first.headers.etag} $changed
    if ($updated.statusCode -ne 200) { throw '条件更新失败' }
    $read=Invoke-Backup 'GET /v1/backup/snapshot' $runId @{} ''
    if ($read.statusCode -ne 200 -or ($read.body | ConvertFrom-Json).backupId -ne ($runId+'-new')) { throw '备份读取不一致' }
    Write-Output '通过：真实 API 拒绝匿名/无效令牌；部署后的 Lambda/S3 通过幂等、隔离、条件更新和恢复读取测试。'
} finally {
    # 仅删除本次随机测试键的版本，永远不按整个桶或真实账号清理。
    $versions=Read-AwsJson @('s3api','list-object-versions','--bucket',$bucket,'--prefix',$key)
    foreach ($version in @($versions.Versions)+@($versions.DeleteMarkers)) {
        if ($null -ne $version -and $version.Key -ceq $key) {
            & aws s3api delete-object --bucket $bucket --key $key --version-id $version.VersionId @awsCommon | Out-Null
            if ($LASTEXITCODE -ne 0) { throw '测试对象清理失败' }
        }
    }
    foreach ($file in @($payloadFile,$responseFile)) { if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file } }
    Remove-Item -LiteralPath $directory
}
