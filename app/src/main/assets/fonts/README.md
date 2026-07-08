# Fonts

The app uses **Dancing Script** by Google Fonts for Latin handwriting animation.

Due to its size, the `.ttf` file is not included in the repository. Download it:

```sh
curl -L -o app/src/main/assets/fonts/DancingScript-Regular.ttf \
  "https://github.com/googlefonts/DancingScript/raw/main/fonts/ttf/DancingScript-Regular.ttf"
```

The CI workflow (`.github/workflows/build.yml`) downloads this automatically.

License: SIL Open Font License 1.1 (see [OFL.txt](OFL.txt))
