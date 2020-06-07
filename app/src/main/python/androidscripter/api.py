class Api:
    def __init__(self, api):
        self._api = api

    def log(self, string):
        self._api.log(string)

    def sleep(self, seconds):
        self._api.sleep(seconds)


    def get_overlay_dimens(self):
        return self._api.getOverlayDimens()

    def foreground_activity_package(self):
        return self._api.foregroundActivityPackage()

    def foreground_window_state(self):
        return self._api.foregroundWindowState()

    def get_screencap(self):
        return self._api.getScreenCap()

    def do_x_search(self):
        return self._api.doXSearch()

    def find_xs_in_screen(self, show_debug_overlay=True):
        return self._api.findXsInScreen(show_debug_overlay)

    def is_network_metered(self):
        '''
        Returns True, False, or None if the network and/or its capabilities could not be determined
        '''
        return self._api.isNetworkMetered()

    def send_click(self, x, y, is_percent=False):
        return self._api.sendClick(x, y, is_percent)

    def press_back(self):
        return self._api.pressBack()

    def press_home(self):
        return self._api.pressHome()

    def press_recent_apps(self):
        return self._api.pressRecentApps()

def newApi(api):
    return Api(api)
