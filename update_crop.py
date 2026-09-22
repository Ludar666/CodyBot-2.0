with open("app/src/main/java/com/codybot/prototype/ScreenCaptureService.java", "r") as f:
    content = f.read()

# Nuove coordinate mirate solo alla fascia del testo dell'indizio
old_crop = '''private Bitmap cropClue(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int left = Math.max(0, Math.round(w * 0.04f));
        int top = Math.max(0, Math.round(h * 0.61f));
        int right = Math.min(w, Math.round(w * 0.96f));
        int bottom = Math.min(h, Math.round(h * 0.67f));
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }'''

new_crop = '''private Bitmap cropClue(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int left = Math.max(0, Math.round(w * 0.05f));
        int top = Math.max(0, Math.round(h * 0.63f));
        int right = Math.min(w, Math.round(w * 0.95f));
        int bottom = Math.min(h, Math.round(h * 0.67f));
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }'''

if "0.61f" in content:
    content = content.replace("0.61f", "0.63f")

with open("app/src/main/java/com/codybot/prototype/ScreenCaptureService.java", "w") as f:
    f.write(content)
