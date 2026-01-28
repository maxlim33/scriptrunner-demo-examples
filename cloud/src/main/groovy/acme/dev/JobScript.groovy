import com.onresolve.scriptrunner.runner.ScriptRunnerImpl

def scriptRunner = ScriptRunnerImpl.getPluginComponent(ScriptRunnerImpl)
log.warn("Running scheduled job")
