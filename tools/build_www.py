"""Genera app/src/main/assets/www/index.html da web/app.html + web/android.js."""
import pathlib, re
root = pathlib.Path(__file__).resolve().parent.parent
src = (root / "web/app.html").read_text(encoding="utf-8")
android = (root / "web/android.js").read_text(encoding="utf-8")

src = re.sub(r'<link rel="preconnect"[^>]*>\s*', "", src)
src = re.sub(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com[^>]*>\s*', "", src)
src = re.sub(r'<meta name="claude:[^>]*>\s*', "", src)
src = re.sub(r"<title>.*?</title>", "<title>TrainUP</title>", src, count=1)

fonts = """<style>
@font-face{font-family:"Karla";src:url("fonts/karla-latin-400-normal.woff2") format("woff2");font-weight:400;font-display:swap}
@font-face{font-family:"Karla";src:url("fonts/karla-latin-500-normal.woff2") format("woff2");font-weight:500;font-display:swap}
@font-face{font-family:"Karla";src:url("fonts/karla-latin-600-normal.woff2") format("woff2");font-weight:600;font-display:swap}
@font-face{font-family:"Karla";src:url("fonts/karla-latin-700-normal.woff2") format("woff2");font-weight:700;font-display:swap}
@font-face{font-family:"Fraunces";src:url("fonts/fraunces-latin-500-normal.woff2") format("woff2");font-weight:500;font-display:swap}
@font-face{font-family:"Fraunces";src:url("fonts/fraunces-latin-600-normal.woff2") format("woff2");font-weight:600;font-display:swap}
</style>
"""
i = src.index('<div id="app">')
head, body = src[:i], src[i:]
j = body.rindex("</script>") + len("</script>")
body = body[:j] + "\n<script>\n" + android + "\n</script>\n" + body[j:]
out = ('<!doctype html>\n<html lang="it">\n<head>\n<meta charset="utf-8">\n'
       '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
       + fonts + head + "</head>\n<body>\n" + body + "\n</body>\n</html>\n")
dst = root / "app/src/main/assets/www/index.html"
dst.write_text(out, encoding="utf-8")
print("scritto", dst, len(out), "byte")
