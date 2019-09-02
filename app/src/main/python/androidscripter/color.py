import java

_jColor = None
def jColor():
    global _jColor
    if _jColor is None:
        _jColor = java.jclass("android.graphics.Color")
    return _jColor

def colorFromInt(num):
    return jColor().valueOf(java.jint(num))

def floatTo255(flt):
   """Convert a number from 0 to 1 to 0 to 255"""
   return int(255 * flt)

def colorToColorTup(colObj):
   return (floatTo255(colObj.red()),
           floatTo255(colObj.green()),
           floatTo255(colObj.blue()))

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
    pix = bitmap.getPixel(int(coord[0]), int(coord[1]))
    col = colorFromInt(pix)
    return colorToColorTup(col)
