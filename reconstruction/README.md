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

## Kemampuan baru: ProotExecutor

`ProotExecutor` adalah kemampuan nyata pertama yang benar-benar mengeksekusi,
bukan hanya memeriksa file:

- Membangun perintah `<proot> -r <rootfs> -w /root /bin/sh -c "<command>"`
  dengan HOME=/root, PATH=/usr/local/bin:/usr/bin:/bin, LANG=C.UTF-8.
  Daftar argumen ini adalah desain rekonstruksi, bukan salinan argumen
  `buildProotCommand()` aslinya (yang tidak sempat di-dump), dan README ini
  menyatakannya supaya tidak diklaim sebagai bukti bytecode.
- `execute()` mengembalikan `CommandResult(exitCode, output, timedOut)`,
  dengan exitCode -1 untuk timeout maupun binary yang tidak ada, mengikuti
  bentuk hasil aslinya.
- `SetupVerifier.verifyWithProot()` menjalankan perintah verify yang sama
  persis dengan aslinya (`node --version`, `codex --version`,
  `codex-web-local --help || node .../dist-cli/index.js --help`, dll).
  Bedanya dengan verify asli: tiap perintah di sini dibatasi timeout
  30 detik (verify asli tidak memakai timeout), supaya UI tidak menggantung.
- `MainActivity` memakai jalur proot ini hanya kalau file `<app files>/proot`
  ada; kalau tidak ada, ia kembali ke jalur cek file. Tidak ada binary
  proot yang dibundel, jadi instalasi baru tetap memakai jalur cek file.

## Kemampuan baru: Penyediaan rootfs

`RootfsManager` menyiapkan rootfs dari tiga sumber yang teramati di 2.0.514,
tanpa mengklaim salah satunya sudah lengkap:

- `RootfsSource.Manual` — file yang sudah ada di perangkat.
- `RootfsSource.Url` — URL langsung yang dimasukkan pengguna di UI.
- `RootfsSource.GitHubRelease` — aset rilis GitHub dengan base URL fallback
  yang teramati (`friuns2/anyclaw-rootfs-assets/releases/download/rootfs-latest/`),
  tetapi nama asetnya TIDAK di-hardcode: nama itu harus diambil dari halaman
  rilisnya sendiri, karena menebaknya berisiko mengunduh file yang salah.
- Play Asset Delivery TIDAK diimplementasikan: itu butuh Asset Pack yang
  dikonfigurasi untuk aplikasi ini.

Alurnya: kalau marker `.rootfs-ready` sudah ada di direktori rootfs,
prepare dilewati (`AlreadyReady`). Kalau belum, arsip diunduh (HTTP 200
saja, timeout connect 30 detik / read 60 detik, progres per chunk),
diekstrak, lalu marker ditulis. Ekstraktor hanya menerima ZIP dan menolak
entri zip-slip (jalur yang keluar dari direktori tujuan) dengan IOException.
Arsip non-ZIP, misalnya tarball, ditolak secara eksplisit, bukan ditebak
formatnya.

PERINGATAN: jangan masukkan URL zip bootstrap sembarangan. Pakai hanya
arsip rootfs yang disediakan/ditautkan oleh rilis yang sama dengan aplikasi
yang dibangun, dan cocokkan hash/checksum bila rilis itu menyediakannya.
UI di aplikasi ini hanya menerima URL ZIP; pemilih file manual
(`Select rootfs file` di 2.0.514) belum tersedia di UI ini dan menjadi
langkah berikutnya yang masuk akal.

## Yang BELUM ada (jangan diklaim sudah jadi)

- Play Asset Delivery, pemilih file rootfs manual di UI, dan binary proot
  itu sendiri (serta node/OpenClaw/codexui di dalam rootfs).
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
