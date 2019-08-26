import java

_jColor = None
def jColor():
    global _jColor
    if _jColor is None:
        _jColor = java.jclass("android.graphics.Color")
    return _jColor

def colorFromInt(num):
    return jColor().valueOf(java.jint(num))
