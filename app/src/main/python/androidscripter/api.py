import java
from typing import Set

class Api:
    _jLogLevel = java.jclass("com.tsiemens.androidscripter.script.Api$LogLevel")

    def __init__(self, api):
        self._api = api

    @staticmethod
    def _logLevel(lvlStr):
        if lvlStr == 'D':
            return Api._jLogLevel.DEBUG
        elif lvlStr == 'V':
            return Api._jLogLevel.VERBOSE
        elif lvlStr == 'W':
            return Api._jLogLevel.WARNING
        elif lvlStr == 'E':
            return Api._jLogLevel.ERROR
        return Api._jLogLevel.INFO

    def log(self, string, level='I'):
        self._api.log(string, Api._logLevel(level))

    def logd(self, string):
        self.log(string, level='D')
    def logv(self, string):
        self.log(string, level='V')
    def logi(self, string):
        self.log(string, level='I')
    def logw(self, string):
        self.log(string, level='W')
    def loge(self, string):
        self.log(string, level='E')

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

    def find_xs_in_screen(self, show_debug_overlay=True):
        return self._api.findXsInScreen(show_debug_overlay)

    def is_network_metered(self):
        '''
        Returns True, False, or None if the network and/or its capabilities could not be determined
        NOTE: this is not always accurate when connected to a VPN
        '''
        return self._api.isNetworkMetered()

    def get_network_transports(self) -> Set[str]:
        '''
        returns a set which can contain any of these strings:
        "bluetooth", "cellular", "ethernet", "vpn", "wifi"
        '''
        transports = self._api.getNetworkTransports()
        return set(t for t in transports.toArray())

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
