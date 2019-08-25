import java

class Api:
    def __init__(self, android_context, api=None):
        if api is not None:
            self._api = api
        else:
            # TODO remove
            ScriptApi = java.jclass("com.tsiemens.androidscripter.ScriptApi")
            self._api = ScriptApi(android_context)

    def log(self, string):
        self._api.log(string)

    def foreground_activity_package(self):
        return self._api.foregroundActivityPackage()

    def foreground_window_state(self):
        return self._api.foregroundWindowState()

def newApi(android_context):
    return Api(android_context)

def newApiFromApi(api):
    return Api(None, api=api)