import java

_jColor = None
def jColor():
    global _jColor
    if _jColor is None:
        _jColor = java.jclass("com.tsiemens.androidscripter.util.ColorCompat")
    return _jColor

def jColorStatic():
    return jColor().Companion

_jBitmapUtil = None
def jBitmapUtil():
    global _jBitmapUtil
    if _jBitmapUtil is None:
        _jBitmapUtil = java.jclass("com.tsiemens.androidscripter.util.BitmapUtil")
    return _jBitmapUtil.Companion

def colorFromInt(num):
    return jColor()(java.jint(num))

def colorToColorTup(colObj):
   return (colObj.red(),
           colObj.green(),
           colObj.blue())

def colorTupToColor(colTup):
    return jColorStatic().rgb(colTup[0], colTup[1], colTup[2])

def tupToColorStr(tup):
    """Takes a 255, 255, 255 based tuple, and converts it to
    a 6 character hex string"""
    return "".join("%02x" % v for v in tup)

def colorStrToTup(colStr):
    """Takes a 6 character color tuple in hex,
    and converts it to a 255, 255, 255 color tuple.
    Case insensitive
    """
    colStr = colStr.lower()
    return tuple(int(s, 16) for s in (colStr[:2], colStr[2:4], colStr[4:]))

def colorDist(col1Tup, col2Tup):
   dr = col1Tup[0] - col2Tup[0]
   dg = col1Tup[1] - col2Tup[1]
   db = col1Tup[2] - col2Tup[2]
   return ((dr**2) + (dg**2) + (db**2))**(0.5)

def colorTupFromBitmapPixel(bitmap, coord):
    col = jBitmapUtil().getPixelColor(coord[0], coord[1], bitmap)
    return colorToColorTup(col)
