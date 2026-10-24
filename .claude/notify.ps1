# Claude Code 작업 완료 시 Windows 알림 표시 (Stop hook)
$title = 'Claude Code'
$body = '작업 완료 - 결과를 확인하세요 (hyundai-contest)'

# 알림 앱 ID 등록 (처음 한 번만 생성됨) — 등록 안 된 ID의 알림은 Windows가 조용히 버림
$appId = 'ClaudeCode.Notify'
$key = "HKCU:\Software\Classes\AppUserModelId\$appId"
if (-not (Test-Path $key)) {
    New-Item -Path $key -Force | Out-Null
    New-ItemProperty -Path $key -Name DisplayName -Value 'Claude Code' -PropertyType String -Force | Out-Null
    New-ItemProperty -Path $key -Name ShowInSettings -Value 1 -PropertyType DWord -Force | Out-Null
}

[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
$xml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
$texts = $xml.GetElementsByTagName('text')
$texts.Item(0).AppendChild($xml.CreateTextNode($title)) | Out-Null
$texts.Item(1).AppendChild($xml.CreateTextNode($body)) | Out-Null
$audio = $xml.CreateElement('audio')
$audio.SetAttribute('src', 'ms-winsoundevent:Notification.Default')
$xml.DocumentElement.AppendChild($audio) | Out-Null
$toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($appId).Show($toast)
