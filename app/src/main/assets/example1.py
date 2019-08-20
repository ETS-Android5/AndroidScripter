import time

def run(api):
    i = 0
    for i in range(50):
        api.log("foo %d" % i)
        time.sleep(0.5)

   # print("Example 1")
   # import time
   # time.sleep(4)
   # print("Fgnd activity:", api.foreground_activity_package())
   # print("end Example 1")