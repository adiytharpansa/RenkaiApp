# RENKAI
Aplikasi Android native (Kotlin + Jetpack Compose + Media3) untuk streaming anime dari katalog gudang.

- Terhubung ke katalog asli (ribuan judul): Cloudflare Pages terenkripsi AES-256-GCM + cermin jsDelivr/raw
- Poster, banner, sinopsis, genre, skor, dan daftar episode per judul
- Subtitle native Inggris + Indonesia dari manifest HLS (dipilih lewat tombol teks di pemutar)
- Pemutar tangguh: pintu CDN dipilih otomatis (jsDelivr beberapa edge / raw) bila satu pintu gagal
- Tema terang/gelap otomatis mengikuti sistem, aksen kuning (#FFD60A)
- HLS (.m3u8) dan DASH (.mpd) via Media3 ExoPlayer; landscape = layar penuh otomatis

Buka folder ini di Android Studio (Ladybug atau lebih baru), Sync, lalu Run.
minSdk 26, targetSdk 35. Build rilis otomatis lewat GitHub Actions (workflow "Build Release APK");
kunci katalog disuntik dari rahasia repo `CATALOG_KEY` (build lokal tanpa kunci tetap jalan, katalog dari cermin).

## Fitur UI
Beranda (hero unggulan + baris Tren/Terbaru/genre/Film), Cari (judul & genre / tempel link), Koleksi (disimpan di memori sesi), halaman detail judul dengan daftar episode, layar pemutar dengan navigasi episode sebelumnya/berikutnya.
