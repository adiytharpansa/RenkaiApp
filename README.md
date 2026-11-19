# RENKAI

Aplikasi Android native (Kotlin + Jetpack Compose + Media3) untuk streaming anime dari katalog gudang.

- Terhubung ke katalog asli (ribuan judul): Cloudflare Pages terenkripsi AES-256-GCM + cermin jsDelivr/raw
- Poster, banner, sinopsis, genre, skor, dan daftar episode per judul
- Subtitle native Inggris + Indonesia dari manifest HLS (dipilih lewat tombol teks di pemutar)
- Pemutar tangguh: pintu CDN dipilih otomatis (jsDelivr beberapa edge / raw) bila satu pintu gagal
- Koleksi tersimpan permanen, posisi episode disimpan untuk dilanjutkan, dan episode berikutnya diputar otomatis
- Layanan sesi media Android menyediakan kontrol pemutaran; video dijeda saat aplikasi ke latar belakang
- Tema terang/gelap otomatis mengikuti sistem, aksen kuning (#FFD60A)
- HLS (.m3u8) dan DASH (.mpd) via Media3 ExoPlayer; landscape = layar penuh otomatis

Buka folder ini di Android Studio (Ladybug atau lebih baru), Sync, lalu Run.
minSdk 26, targetSdk 35. GitHub Actions membangun APK release sebagai artefak pada push ke `main`; menjalankan workflow `Build Release APK` secara manual akan menerbitkan atau memperbarui GitHub Release v1.0. Workflow `Uji Emulator` membangun APK debug untuk pengujian.
Kunci katalog disuntik dari rahasia repo `CATALOG_KEY` (build lokal tanpa kunci tetap jalan, katalog dari cermin).

## Fitur UI

Beranda (hero unggulan + baris Tren/Terbaru/genre/Film), Cari (judul & genre / tempel link), Koleksi, halaman detail judul dengan daftar episode, dan layar pemutar dengan navigasi episode sebelumnya/berikutnya.
