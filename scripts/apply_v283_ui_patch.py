from pathlib import Path

path = Path("app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt")
text = path.read_text(encoding="utf-8")
text = text.replace('V28Section.VIDEOS -> "TV, portais e conteúdo audiovisual"', 'V28Section.VIDEOS -> "TV, portais, YouTube e conteúdo audiovisual"')
text = text.replace('Text("2.8.2", color = V28Accent', 'Text(BuildConfig.VERSION_NAME, color = V28Accent')
path.write_text(text, encoding="utf-8")
print("MainActivityV28.kt updated for v2.8.3")
