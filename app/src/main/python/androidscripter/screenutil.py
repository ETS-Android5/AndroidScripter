from androidscripter import color

"""
General utilities to be used with screen capture bitmaps
"""

def getPixelSampleCoords(api, upperLeftTup, lowerRightTup, pixPerSample=20):
   """
   Helper to get a subset of pixels from a region of a bitmap.
   Provide the upper left and lower right points of a rectangle on the screen.

   Returned is a generator for pixel tuple coordinates that are to be sampled.

   Uses a dumb sampling algorithm right now, based on a percentage. Defaults to
   1 sample per 20 pixels (5% sample).
   """
   ulx, uly = upperLeftTup
   lrx, lry = lowerRightTup

   width = lrx - ulx
   height = lry - uly

   pixels = width * height
   nSamples = (pixels/pixPerSample)

   x, y = (0, 0)
   i = 0
   while i < nSamples:
      yield (x + ulx, y + uly)
      x = (x + pixPerSample) % width
      y += int((x + pixPerSample) / width)
      i += 1

def checkScreenColorAtPoint(api, coord, targetColorStr, portrait=True, dist=50.0,
                            debugLogging=False):
   """
   Returns True if coord is within dist of the color on the screen, or False if it
   does not.
   Returns None if it was unable to make a determination, such as if the orientation
   did not match.

   coord should be a tuple of two percentages (0.0-1.0).
   """
   bm = api.get_screencap()
   if bm is not None:
      bw = bm.getWidth()
      bh = bm.getHeight()
      if (bh > bw) != portrait:
         api.log("checkScreenColorAtPoint: orientation mismatch")
         return None

      adjCoord = ( int(float(coord[0]) * bw),
                   int(float(coord[1]) * bh) )
      if bw < adjCoord[0] or bh < adjCoord[1]:
         api.log("coord %r is too big for bitmap %d x %d" % (adjCoord, bw, bh))
         return None
      colTup = color.colorTupFromBitmapPixel(bm, adjCoord)
      targetColorTup = color.colorStrToTup(targetColorStr)
      calcDist = color.colorDist(colTup, targetColorTup)
      if debugLogging:
         api.log("Color at %r: #%s" % (adjCoord, color.tupToColorStr(colTup)))
      return calcDist <= dist
   else:
      api.log("screen bitmap was None")
      return None