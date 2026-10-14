# unifeed 서버 접속 (Windows 10/11)
#
#   connect-windows.cmd 를 더블클릭      → 바로 접속 (비밀번호를 물어본다)
#   connect-windows.cmd setup            → %USERPROFILE%\.ssh\config 에 'unifeed' 등록만. 그다음부터는 `ssh unifeed`
#
# 서버: example.com  포트 47330  계정 ubuntu
# ⚠️ 한국 IP 에서만 접속된다 (해외 VPN·해외 로밍이면 막힌다).
# ⚠️ 비밀번호를 여러 번 틀리면 그 IP 가 잠시 차단된다(fail2ban). 몇 분 뒤 다시 시도.
# 웹: https://example.com  ← 서버 안에서 80 포트로 띄운 앱이 여기로 보인다.
param([string]$Mode = '')

$HostAlias = 'unifeed'
$HostName  = 'example.com'
$Port      = 47330
$UserName  = 'ubuntu'

$ssh = Get-Command ssh.exe -ErrorAction SilentlyContinue
if (-not $ssh) {
    Write-Host 'ssh 가 없습니다. 설정 > 앱 > 선택적 기능 > "OpenSSH 클라이언트" 를 추가한 뒤 다시 실행하세요.'
    Write-Host '(관리자 PowerShell 이면: Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0)'
    Read-Host '엔터를 누르면 닫힙니다'; exit 1
}

if ($Mode -eq 'setup') {
    $dir = Join-Path $env:USERPROFILE '.ssh'
    New-Item -ItemType Directory -Force $dir | Out-Null
    $cfg = Join-Path $dir 'config'
    if (-not (Test-Path $cfg)) { New-Item -ItemType File $cfg | Out-Null }
    if (Select-String -Path $cfg -Pattern "^Host\s+$HostAlias(\s|$)" -Quiet) {
        Write-Host "이미 등록돼 있어요: ssh $HostAlias"
    } else {
        $block = "`r`nHost $HostAlias`r`n    HostName $HostName`r`n    Port $Port`r`n    User $UserName`r`n    ServerAliveInterval 30`r`n    ServerAliveCountMax 4`r`n"
        # ssh.exe 는 BOM 있는 config 를 못 읽는다 → BOM 없는 UTF-8 로 덧붙인다
        [IO.File]::AppendAllText($cfg, $block, (New-Object Text.UTF8Encoding $false))
        Write-Host "등록했어요. 이제 PowerShell/명령 프롬프트에서: ssh $HostAlias"
    }
    exit 0
}

& $ssh.Source -p $Port -o ServerAliveInterval=30 -o ServerAliveCountMax=4 "$UserName@$HostName"
if ($LASTEXITCODE -ne 0) { Read-Host "접속이 끝났거나 실패했어요(코드 $LASTEXITCODE). 엔터를 누르면 닫힙니다" }
