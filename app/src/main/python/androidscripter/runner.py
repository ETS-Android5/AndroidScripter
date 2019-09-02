import traceback
import java

def run_script(api, script_module):
    try:
        script_module.run(api)
    except Exception as e:
        if not isinstance(e, java.jclass("java.lang.InterruptedException")):
            raise Exception(str(e) + " :\n" + traceback.format_exc())
        raise