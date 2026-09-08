from pathlib import Path

main = Path('app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt')
s = main.read_text()

news_old = '''            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir notícia", maxLines = 1, fontSize = 10.8.sp)
                }
                OutlinedButton(
                    onClick = { v28ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1, fontSize = 10.8.sp)
                }
            }
'''
news_new = '''            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Abrir", maxLines = 1, fontSize = 10.5.sp)
                }
                OutlinedButton(
                    onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("Link da notícia", n.link))
                        android.widget.Toast.makeText(context, "Link copiado", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Accent)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar link", maxLines = 1, fontSize = 10.2.sp)
                }
                OutlinedButton(
                    onClick = { v28ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp", maxLines = 1, fontSize = 10.2.sp)
                }
            }
'''
if news_old not in s:
    raise SystemExit('news action row not found')
s = s.replace(news_old, news_new, 1)

video_old = '''            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = openVideo, modifier = Modifier.fillMaxWidth().height(42.dp)) {
                Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Abrir vídeo")
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(15.dp))
            }
'''
video_new = '''            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = openVideo, modifier = Modifier.weight(1f).height(46.dp)) {
                    Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir vídeo", maxLines = 1, fontSize = 10.8.sp)
                }
                OutlinedButton(
                    onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("Link do vídeo", item.link))
                        android.widget.Toast.makeText(context, "Link copiado", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Accent)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar link", maxLines = 1, fontSize = 10.8.sp)
                }
            }
'''
if video_old not in s:
    raise SystemExit('video action button not found')
s = s.replace(video_old, video_new, 1)
main.write_text(s)

build = Path('app/build.gradle.kts')
b = build.read_text()
if 'versionCode = 403' not in b or 'versionName = "4.0.3"' not in b:
    raise SystemExit('expected v4.0.3 version not found')
b = b.replace('versionCode = 403', 'versionCode = 404', 1)
b = b.replace('versionName = "4.0.3"', 'versionName = "4.0.4"', 1)
build.write_text(b)
