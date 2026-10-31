# Anyclaw Reconstruction

Proyek Android baru di branch `reconstruction-514`, dibuat dari observasi
statis (REA / androguard) terhadap APK AnyClaw 2.0.514, **bukan** salinan
decompile APK tersebut dan **bukan** APK 2.0.514 itu sendiri.

Package baru: `app.anyclaw.reconstruction`, minSdk 26, targetSdk 35,
version `0.1.0-reconstruction`. Branch ini tidak mengubah `main` di Anyclaw.

## Yang sudah direkonstruksi (dari bukti bytecode)

Urutan verifikasi setup, sama seperti `SetupManager.verify()` yang teramati:

proot -> Node.js -> OpenClaw -> Codex CLI -> codex-web-local -> OpenSSH,
dengan Chromium hanya sebagai peringatan (non-fatal).

- Langkah codex-web-local memakai logika `||` yang sama dengan perintah
  aslinya:
  `codex-web-local --help || node /usr/local/lib/node_modules/codexui-android/dist-cli/index.js --help`
  Di rekonstruksi ini, karena belum ada node/rootfs sungguhan, langkah
  itu diwakili oleh keberadaan shim atau file `dist-cli/index.js`.
  Kalau keduanya tidak ada, pesannya sama: `codex-web-local validation failed`.
- Cek start-time `validateInstalledCodexuiLatest()` diwakili oleh
  `CodexuiValidator`: file `dist-cli/index.js` harus ada dan modul
  `express/lib/request` harus bisa ditemukan di bawah node_modules yang
  sama. Aslinya cek ini dijalankan node dengan timeout 20 detik; di sini
  itu hanya proksi berbasis file, dan README ini menyatakannya secara
  eksplisit supaya tidak diklaim sebagai bukti node bisa memuat express.
- Jalur repair aslinya didokumentasikan di `CodexuiValidator.describeRepair()`,
  tidak dijalankan: staging `pnpm add codexui-android@latest`, validasi di
  staging, lalu tukar ke live dengan backup/restore.

## Yang BELUM ada (jangan diklaim sudah jadi)

- Bootstrap proot / rootfs (Play Asset Delivery maupun unduhan GitHub),
  import rootfs manual, dan eksekusi perintah di dalam proot.
- Binary node, OpenClaw, Codex CLI, dan OpenSSH sungguhan.
- Bundle codexui-android, gateway OpenClaw, WebView UI, login/auth,
  dan semua fitur chat/agent dari 2.0.514.
- Perbaikan untuk kegagalan setup 92% di HP pengguna: kerangka ini hanya
  membuat kegagalan itu terlihat per langkah, bukan memperbaikinya di
  perangkat yang sudah punya instalasi rusak.

## Build

Lokal (butuh JDK 17 + Android SDK + Gradle 8.11.1):

```sh
cd reconstruction
gradle assembleDebug
gradle testDebugUnitTest
```

CI: workflow `.github/workflows/build-reconstruction.yml` berjalan otomatis
saat push ke branch `reconstruction-514` (dan bisa dijalankan manual lewat
workflow_dispatch). Artefaknya adalah APK debug rekonstruksi, bukan APK
rilis 2.0.514.
