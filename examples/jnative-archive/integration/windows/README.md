# Windows shell integration

| Surface | Status |
|--------|--------|
| Classic context menu (`HKCU\Software\Classes\...\shell\JNativeArchive*`) | Implemented (script). **WINDOWS_E2E_UNVERIFIED** |
| Windows 11 modern top-level menu (sparse package / `IExplorerCommand`) | **FUTURE** — not implemented; classic menu must not be described as modern menu |
| Default ZIP file association takeover | **Not done** (by design) |

## Install / uninstall

```powershell
# Current user only — no admin required for HKCU
$env:JN_ARCHIVE_INSTALL_YES = "1"
.\integration\windows\install-classic-context-menu.ps1
.\integration\windows\uninstall-classic-context-menu.ps1
```

Set `JNATIVE_ARCHIVE_EXE` if the GUI binary is not at `build\jn-archive-gui.exe`.
