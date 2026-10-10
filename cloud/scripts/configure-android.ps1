param([ValidateSet('dev','prod')][string]$Stage='prod', [string]$Profile='my-aws', [string]$Region='ap-east-1')
$ErrorActionPreference='Stop'
$values=@{}
foreach ($kind in @('Data','Service')) {
    $json=& aws cloudformation describe-stacks --stack-name "PersonalCalendar-$Stage-$kind" --profile $Profile --region $Region --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw '无法读取云部署配置' }
    ($json | ConvertFrom-Json).Stacks[0].Outputs | ForEach-Object { $values[$_.OutputKey]=$_.OutputValue }
}
if (!$values.ApiUrl -or !$values.AuthDomain -or !$values.ClientId) { throw '云部署配置不完整' }
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$configuration="CLOUD_API_URL=$($values.ApiUrl)`nCLOUD_AUTH_DOMAIN=$($values.AuthDomain)`nCLOUD_CLIENT_ID=$($values.ClientId)`n"
[IO.File]::WriteAllText((Join-Path $root 'cloud-config.properties'),$configuration,[Text.UTF8Encoding]::new($false))
Write-Output "已为安卓写入 $Stage 环境公开配置（不包含密码或令牌）。请重新构建 APK。"
