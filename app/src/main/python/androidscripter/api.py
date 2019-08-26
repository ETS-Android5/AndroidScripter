import java

class Api:
    def __init__(self, api):
        self._api = api

    def log(self, string):
        self._api.log(string)

    def foreground_activity_package(self):
        return self._api.foregroundActivityPackage()

    def foreground_window_state(self):
        return self._api.foregroundWindowState()

    def send_click(self, x, y, is_percent=False):
        return self._api.sendClick(x, y, is_percent)

    def press_back(self):
        return self._api.pressBack()

def newApi(api):
    return Api(api)
