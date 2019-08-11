import java

class Api:
    def __init__(self, android_context):
        ScriptApi = java.jclass("com.tsiemens.androidscripter.ScriptApi")
        self._api = ScriptApi(android_context)

    def foreground_activity_package(self):
        return self._api.foregroundActivityPackage()

def newApi(android_context):
    return Api(android_context)