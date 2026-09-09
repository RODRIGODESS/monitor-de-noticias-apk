from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt"
V30 = ROOT / "app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one old block, found {count}")
    return text.replace(old, new, 1)


def patch_main() -> None:
    s = MAIN.read_text()

    old_colors = '''private val V28Bg = Color(0xFF07101D)
private val V28Surface = Color(0xFF0E1A2A)
private val V28Surface2 = Color(0xFF132238)
private val V28Selected = Color(0xFF17365F)
private val V28Accent = Color(0xFF5EA2FF)
private val V28Mint = Color(0xFF39D6A2)
private val V28Amber = Color(0xFFFFB45E)
private val V28Purple = Color(0xFFA57BFF)
private val V28Red = Color(0xFFFF7777)
private val V28Text = Color(0xFFF5F8FC)
private val V28Text2 = Color(0xFFAEBBD0)
private val V28Divider = Color(0xFF21334A)'''
    new_colors = '''private val V28Bg = Color(0xFF07111F)
private val V28Surface = Color(0xFF0C1828)
private val V28Surface2 = Color(0xFF12243A)
private val V28Selected = Color(0xFF153B60)
private val V28Accent = Color(0xFF58A6FF)
private val V28Mint = Color(0xFF35CFA0)
private val V28Amber = Color(0xFFF0B35D)
private val V28Purple = Color(0xFF9B8CFF)
private val V28Red = Color(0xFFFF6B7A)
private val V28Text = Color(0xFFF4F7FB)
private val V28Text2 = Color(0xFF9FB0C5)
private val V28Divider = Color(0xFF203449)'''
    s = replace_once(s, old_colors, new_colors, "main colors")

    old_top = '''    Surface(color = V28Bg, modifier = Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(V28Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(if (section == V28Section.VIDEOS) Icons.Outlined.PlayCircle else Icons.Outlined.Radar, null, tint = V28Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = V28Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = V28Accent.copy(alpha = .10f), shape = RoundedCornerShape(9.dp)) {
                Text(BuildConfig.VERSION_NAME, color = V28Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }'''
    new_top = '''    Surface(
        color = V28Surface,
        border = BorderStroke(1.dp, V28Divider.copy(alpha = .75f)),
        modifier = Modifier.statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = V28Accent.copy(alpha = .10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, V28Accent.copy(alpha = .18f))
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(if (section == V28Section.VIDEOS) Icons.Outlined.PlayCircle else Icons.Outlined.Radar, null, tint = V28Accent, modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CENTRAL DE MONITORAMENTO", color = V28Accent, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(title, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = V28Text2, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                color = V28Surface2,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, V28Divider)
            ) {
                Text("v${BuildConfig.VERSION_NAME}", color = V28Text2, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
    }'''
    s = replace_once(s, old_top, new_top, "professional top bar")

    old_bottom = '''    Surface(color = Color(0xFF0A1422), tonalElevation = 8.dp, border = BorderStroke(1.dp, V28Divider.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            nav.forEach { item ->
                V28NavButton(item.label, item.icon, section == item.section, Modifier.weight(1f)) { onSection(item.section) }
            }
            V28NavButton("Mais", Icons.Outlined.MoreHoriz, moreSelected, Modifier.weight(1f), onMore)
        }
    }'''
    new_bottom = '''    Surface(
        color = V28Surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, V28Divider.copy(alpha = .85f)),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            nav.forEach { item ->
                V28NavButton(item.label, item.icon, section == item.section, Modifier.weight(1f)) { onSection(item.section) }
            }
            V28NavButton("Mais", Icons.Outlined.MoreHoriz, moreSelected, Modifier.weight(1f), onMore)
        }
    }'''
    s = replace_once(s, old_bottom, new_bottom, "professional bottom bar")

    old_nav = '''    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) V28Accent.copy(alpha = .14f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (selected) V28Accent else V28Text2, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (selected) V28Accent else V28Text2, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }'''
    new_nav = '''    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.width(28.dp).height(2.dp).clip(RoundedCornerShape(99.dp))
                .background(if (selected) V28Accent else Color.Transparent)
        )
        Spacer(Modifier.height(6.dp))
        Icon(icon, label, tint = if (selected) V28Accent else V28Text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) V28Text else V28Text2, fontSize = 9.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }'''
    s = replace_once(s, old_nav, new_nav, "professional nav item")

    MAIN.write_text(s)


def patch_v30() -> None:
    s = V30.read_text()

    old_colors = '''private val V30Bg = Color(0xFF07101D)
private val V30Surface = Color(0xFF0E1A2A)
private val V30Surface2 = Color(0xFF132238)
private val V30Selected = Color(0xFF17365F)
private val V30Accent = Color(0xFF5EA2FF)
private val V30Mint = Color(0xFF39D6A2)
private val V30Amber = Color(0xFFFFB45E)
private val V30Purple = Color(0xFFA57BFF)
private val V30Red = Color(0xFFFF7777)
private val V30Text2 = Color(0xFFAEBBD0)
private val V30Divider = Color(0xFF21334A)'''
    new_colors = '''private val V30Bg = Color(0xFF07111F)
private val V30Surface = Color(0xFF0C1828)
private val V30Surface2 = Color(0xFF12243A)
private val V30Selected = Color(0xFF153B60)
private val V30Accent = Color(0xFF58A6FF)
private val V30Mint = Color(0xFF35CFA0)
private val V30Amber = Color(0xFFF0B35D)
private val V30Purple = Color(0xFF9B8CFF)
private val V30Red = Color(0xFFFF6B7A)
private val V30Text2 = Color(0xFF9FB0C5)
private val V30Divider = Color(0xFF203449)'''
    s = replace_once(s, old_colors, new_colors, "v30 colors")

    old_metric = '''    Surface(color = V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Divider), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.weight(1f)); Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(6.dp)); Text(label, color = V30Text2, fontSize = 11.5.sp)
        }
    }'''
    new_metric = '''    Surface(color = V30Surface, shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, border = BorderStroke(1.dp, V30Divider), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(9.dp)) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, color = V30Text2, fontSize = 10.8.sp, fontWeight = FontWeight.SemiBold)
        }
    }'''
    s = replace_once(s, old_metric, new_metric, "professional metric card")

    old_video_surface = '''    Surface(color = V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth().clickable(onClick = open)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {'''
    new_video_surface = '''    Surface(color = V30Surface, shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth().clickable(onClick = open)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(V30Purple.copy(alpha = .72f)))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {'''
    s = replace_once(s, old_video_surface, new_video_surface, "professional video card")

    old_news_surface = '''    Surface(color = V30Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth().clickable {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) }
    }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {'''
    new_news_surface = '''    Surface(color = V30Surface, shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, border = BorderStroke(1.dp, V30Divider), modifier = Modifier.fillMaxWidth().clickable {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) }
    }) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(V30Accent.copy(alpha = .72f)))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {'''
    s = replace_once(s, old_news_surface, new_news_surface, "professional news card")

    old_source_surface = '''        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) V30Accent.copy(alpha = .45f) else V30Divider),'''
    new_source_surface = '''        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 1.dp else 0.dp,
        border = BorderStroke(1.dp, if (selected) V30Accent.copy(alpha = .45f) else V30Divider),'''
    s = replace_once(s, old_source_surface, new_source_surface, "professional source card")

    V30.write_text(s)


patch_main()
patch_v30()
print("v4.1.0 professional UI patch applied")
