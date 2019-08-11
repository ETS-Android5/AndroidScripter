from types import ModuleType

def compile_module(moduleName, codeStr):
    code = compile(codeStr, '', 'exec')
    module = ModuleType(moduleName)
    exec(code, module.__dict__)
    return module