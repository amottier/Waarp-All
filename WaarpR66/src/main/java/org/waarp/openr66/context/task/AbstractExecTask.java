/*
 * This file is part of Waarp Project (named also Waarp or GG).
 *
 *  Copyright (c) 2019, Waarp SAS, and individual contributors by the @author
 *  tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 *  All Waarp Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 * Waarp . If not, see <http://www.gnu.org/licenses/>.
 */
package org.waarp.openr66.context.task;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.waarp.common.command.exception.CommandAbstractException;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.openr66.context.ErrorCode;
import org.waarp.openr66.context.R66Result;
import org.waarp.openr66.context.R66Session;
import org.waarp.openr66.context.filesystem.R66Dir;
import org.waarp.openr66.context.filesystem.R66File;
import org.waarp.openr66.context.task.exception.OpenR66RunnerErrorException;
import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolNoSslException;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Execute an external command
 * <p>
 * It provides some common functionnalities.
 */
public abstract class AbstractExecTask extends AbstractTask {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(AbstractExecTask.class);

  /**
   * Constructor
   *
   * @param type
   * @param delay
   * @param argRule
   * @param session
   */
  AbstractExecTask(TaskType type, int delay, String argRule, String argTransfer,
                   R66Session session) {
    super(type, delay, argRule, argTransfer, session);
  }

  /**
   * Apply transferInfo substitutions
   *
   * @param line the line to format
   *
   * @return the line after substitutions
   */
  protected String applyTransferSubstitutions(String line) {
    final Object[] argFormat = argTransfer.split(" ");
    if (argFormat != null && argFormat.length > 0) {
      try {
        return String.format(line, argFormat);
      } catch (final Exception e) {
        // ignored error since bad argument in static rule info
        logger.error("Bad format in Rule: {" + line + "} " + e.getMessage());
      }
    }
    return line;
  }

  /**
   * Generates a Command line object from rule and transfer data
   *
   * @param line the command to process as a string
   */
  protected CommandLine buildCommandLine(String line) {
    if (line.contains(NOWAIT)) {
      waitForValidation = false;
    }
    if (line.contains(LOCALEXEC)) {
      useLocalExec = true;
    }

    final String replacedLine = line.replaceAll("#([A-Z]+)#", "\\${$1}");

    final CommandLine commandLine =
        CommandLine.parse(replacedLine, getSubstitutionMap());

    final File exec = new File(commandLine.getExecutable());
    if (exec.isAbsolute()) {
      if (!exec.canExecute()) {
        logger.error("Exec command is not executable: " + line);
        final R66Result result =
            new R66Result(session, false, ErrorCode.CommandNotFound,
                          session.getRunner());
        futureCompletion.setResult(result);
        futureCompletion.cancel();
        return null;
      }
    }

    return commandLine;
  }

  /**
   * Generates a substitution map as expected by Apache Commons Exec
   * CommandLine
   */
  protected Map<String, Object> getSubstitutionMap() {
    final Map<String, Object> rv = new HashMap<String, Object>();
    rv.put(NOWAIT.replace("#", ""), "");
    rv.put(LOCALEXEC.replace("#", ""), "");

    File trueFile = null;
    if (session.getFile() != null) {
      trueFile = session.getFile().getTrueFile();
    }
    if (trueFile != null) {
      rv.put(TRUEFULLPATH.replace("#", ""), trueFile.getAbsolutePath());
      rv.put(TRUEFILENAME.replace("#", ""),
             R66Dir.getFinalUniqueFilename(session.getFile()));
      rv.put(FILESIZE.replace("#", ""), Long.toString(trueFile.length()));
    } else {
      rv.put(TRUEFULLPATH.replace("#", ""), "nofile");
      rv.put(TRUEFILENAME.replace("#", ""), "nofile");
      rv.put(FILESIZE.replace("#", ""), "0");
    }

    final DbTaskRunner runner = session.getRunner();
    if (runner != null) {
      rv.put(ORIGINALFULLPATH.replace("#", ""), runner.getOriginalFilename());
      rv.put(ORIGINALFILENAME.replace("#", ""),
             R66File.getBasename(runner.getOriginalFilename()));
      rv.put(RULE.replace("#", ""), runner.getRuleId());
    }

    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    final Date date = new Date();
    rv.put(DATE.replace("#", ""), dateFormat.format(date));
    dateFormat = new SimpleDateFormat("HHmmss");
    rv.put(HOUR.replace("#", ""), dateFormat.format(date));

    if (session.getAuth() != null) {
      rv.put(REMOTEHOST.replace("#", ""), session.getAuth().getUser());
      String localhost = "";
      try {
        localhost =
            Configuration.configuration.getHostId(session.getAuth().isSsl());
      } catch (final OpenR66ProtocolNoSslException e) {
        // replace by standard name
        localhost = Configuration.configuration.getHOST_ID();
      }
      rv.put(LOCALHOST.replace("#", ""), localhost);
    }
    if (session.getRemoteAddress() != null) {
      rv.put(REMOTEHOSTADDR.replace("#", ""),
             session.getRemoteAddress().toString());
      rv.put(LOCALHOSTADDR.replace("#", ""),
             session.getLocalAddress().toString());
    } else {
      rv.put(REMOTEHOSTADDR.replace("#", ""), "unknown");
      rv.put(LOCALHOSTADDR.replace("#", ""), "unknown");
    }
    if (runner != null) {
      rv.put(TRANSFERID.replace("#", ""), runner.getSpecialId());
      rv.put(REQUESTERHOST.replace("#", ""), runner.getRequester());
      rv.put(REQUESTEDHOST.replace("#", ""), runner.getRequested());
      rv.put(FULLTRANSFERID.replace("#", ""),
             runner.getSpecialId() + "_" + runner.getRequester() + "_" +
             runner.getRequested());
      rv.put(RANKTRANSFER.replace("#", ""), Integer.toString(runner.getRank()));
    }
    rv.put(BLOCKSIZE.replace("#", ""), session.getBlockSize());

    R66Dir dir = new R66Dir(session);
    if (runner != null) {
      if (runner.isRecvThrough() || runner.isSendThrough()) {
        try {
          dir.changeDirectoryNotChecked(runner.getRule().getRecvPath());
          rv.put(INPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
        dir = new R66Dir(session);
        try {
          dir.changeDirectoryNotChecked(runner.getRule().getSendPath());
          rv.put(OUTPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
        dir = new R66Dir(session);
        try {
          dir.changeDirectoryNotChecked(runner.getRule().getWorkPath());
          rv.put(WORKPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
        dir = new R66Dir(session);
        try {
          dir.changeDirectoryNotChecked(runner.getRule().getArchivePath());
          rv.put(ARCHPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
      } else {
        try {
          dir.changeDirectory(runner.getRule().getRecvPath());
          rv.put(INPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
        dir = new R66Dir(session);
        try {
          dir.changeDirectory(runner.getRule().getSendPath());
          rv.put(OUTPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
        dir = new R66Dir(session);
        try {
          dir.changeDirectory(runner.getRule().getWorkPath());
          rv.put(WORKPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
        dir = new R66Dir(session);
        try {
          dir.changeDirectory(runner.getRule().getArchivePath());
          rv.put(ARCHPATH.replace("#", ""), dir.getFullPath());
        } catch (final CommandAbstractException e) {
        }
      }
    } else {
      try {
        dir.changeDirectory(Configuration.configuration.getInPath());
        rv.put(INPATH.replace("#", ""), dir.getFullPath());
      } catch (final CommandAbstractException e) {
      }
      dir = new R66Dir(session);
      try {
        dir.changeDirectory(Configuration.configuration.getOutPath());
        rv.put(OUTPATH.replace("#", ""), dir.getFullPath());
      } catch (final CommandAbstractException e) {
      }
      dir = new R66Dir(session);
      try {
        dir.changeDirectory(Configuration.configuration.getWorkingPath());
        rv.put(WORKPATH.replace("#", ""), dir.getFullPath());
      } catch (final CommandAbstractException e) {
      }
      dir = new R66Dir(session);
      try {
        dir.changeDirectory(Configuration.configuration.getArchivePath());
        rv.put(ARCHPATH.replace("#", ""), dir.getFullPath());
      } catch (final CommandAbstractException e) {
      }
    }
    rv.put(HOMEPATH.replace("#", ""),
           Configuration.configuration.getBaseDirectory());
    if (session.getLocalChannelReference() == null) {
      rv.put(ERRORMSG.replace("#", ""), "NoError");
      rv.put(ERRORCODE.replace("#", ""), "-");
      rv.put(ERRORSTRCODE.replace("#", ""), ErrorCode.Unknown.name());
    } else {
      try {
        rv.put(ERRORMSG.replace("#", ""),
               session.getLocalChannelReference().getErrorMessage());
      } catch (final NullPointerException e) {
        rv.put(ERRORMSG.replace("#", ""), "NoError");
      }
      try {
        rv.put(ERRORCODE.replace("#", ""),
               session.getLocalChannelReference().getCurrentCode().getCode());
      } catch (final NullPointerException e) {
        rv.put(ERRORCODE.replace("#", ""), "-");
      }
      try {
        rv.put(ERRORSTRCODE.replace("#", ""),
               session.getLocalChannelReference().getCurrentCode().name());
      } catch (final NullPointerException e) {
        rv.put(ERRORSTRCODE.replace("#", ""), ErrorCode.Unknown.name());
      }
    }
    return rv;
  }

  /**
   * For External command execution
   */
  class PrepareCommandExec {
    private final boolean noOutput;
    private final boolean waitForValidation;
    private final String finalname;
    private boolean myResult;
    private CommandLine commandLine;
    private DefaultExecutor defaultExecutor;
    private PipedInputStream inputStream;
    private PipedOutputStream outputStream;
    private PumpStreamHandler pumpStreamHandler;
    private ExecuteWatchdog watchdog;

    public PrepareCommandExec(final String finalname, final boolean noOutput,
                              boolean waitForValidation) {
      this.finalname = finalname;
      this.noOutput = noOutput;
      this.waitForValidation = waitForValidation;
    }

    boolean isError() {
      return myResult;
    }

    public CommandLine getCommandLine() {
      return commandLine;
    }

    public DefaultExecutor getDefaultExecutor() {
      return defaultExecutor;
    }

    public PipedInputStream getInputStream() {
      return inputStream;
    }

    public PipedOutputStream getOutputStream() {
      return outputStream;
    }

    public PumpStreamHandler getPumpStreamHandler() {
      return pumpStreamHandler;
    }

    public ExecuteWatchdog getWatchdog() {
      return watchdog;
    }

    public PrepareCommandExec invoke() {
      commandLine = buildCommandLine(finalname);
      if (commandLine == null) {
        myResult = true;
        return this;
      }

      defaultExecutor = new DefaultExecutor();
      if (noOutput) {
        pumpStreamHandler = new PumpStreamHandler(null, null);
      } else {
        inputStream = new PipedInputStream();
        outputStream = null;
        try {
          outputStream = new PipedOutputStream(inputStream);
        } catch (final IOException e1) {
          try {
            inputStream.close();
          } catch (final IOException e) {
          }
          logger.error(
              "Exception: " + e1.getMessage() + " Exec in error with " +
              commandLine.toString(), e1);
          futureCompletion.setFailure(e1);
          myResult = true;
          return this;
        }
        pumpStreamHandler = new PumpStreamHandler(outputStream, null);
      }
      defaultExecutor.setStreamHandler(pumpStreamHandler);
      final int[] correctValues = { 0, 1 };
      defaultExecutor.setExitValues(correctValues);
      watchdog = null;
      if (delay > 0 && waitForValidation) {
        watchdog = new ExecuteWatchdog(delay);
        defaultExecutor.setWatchdog(watchdog);
      }
      myResult = false;
      return this;
    }
  }

  /**
   * For External command execution
   */
  class ExecuteCommand {
    private final CommandLine commandLine;
    private final DefaultExecutor defaultExecutor;
    private final PipedInputStream inputStream;
    private final PipedOutputStream outputStream;
    private final PumpStreamHandler pumpStreamHandler;
    private final Thread thread;
    private boolean myResult;
    private int status;

    public ExecuteCommand(final CommandLine commandLine,
                          final DefaultExecutor defaultExecutor,
                          final PipedInputStream inputStream,
                          final PipedOutputStream outputStream,
                          final PumpStreamHandler pumpStreamHandler,
                          final Thread thread) {
      this.commandLine = commandLine;
      this.defaultExecutor = defaultExecutor;
      this.inputStream = inputStream;
      this.outputStream = outputStream;
      this.pumpStreamHandler = pumpStreamHandler;
      this.thread = thread;
    }

    boolean isError() {
      return myResult;
    }

    public int getStatus() {
      return status;
    }

    public ExecuteCommand invoke() {
      status = -1;
      try {
        status = defaultExecutor.execute(commandLine);
      } catch (final ExecuteException e) {
        if (e.getExitValue() == -559038737) {
          // Cannot run immediately so retry once
          try {
            Thread.sleep(Configuration.RETRYINMS);
          } catch (final InterruptedException e1) {
          }
          try {
            status = defaultExecutor.execute(commandLine);
          } catch (final ExecuteException e1) {
            closeAllForExecution(true);
            finalizeFromError(thread, status, commandLine, e1);
            myResult = true;
            return this;
          } catch (final IOException e1) {
            closeAllForExecution(true);
            logger.error(
                "IOException: " + e.getMessage() + " . Exec in error with " +
                commandLine.toString());
            futureCompletion.setFailure(e);
            myResult = true;
            return this;
          }
        } else {
          closeAllForExecution(true);
          finalizeFromError(thread, status, commandLine, e);
          myResult = true;
          return this;
        }
      } catch (final IOException e) {
        closeAllForExecution(true);
        logger.error(
            "IOException: " + e.getMessage() + " . Exec in error with " +
            commandLine.toString());
        futureCompletion.setFailure(e);
        myResult = true;
        return this;
      }
      closeAllForExecution(false);
      if (thread != null) {
        try {
          if (delay > 0) {
            thread.join(delay);
          } else {
            thread.join();
          }
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (final IOException e1) {
        }
      }
      myResult = false;
      return this;
    }

    private void closeAllForExecution(boolean interrupt) {
      if (outputStream != null) {
        try {
          outputStream.flush();
        } catch (final IOException e2) {
        }
        try {
          outputStream.close();
        } catch (final IOException e2) {
        }
      }
      if (interrupt && thread != null) {
        thread.interrupt();
      }
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (final IOException e2) {
        }
      }
      try {
        pumpStreamHandler.stop();
      } catch (final IOException e2) {
      }
    }
  }

  void finalizeFromError(Runnable threadReader, int status,
                         CommandLine commandLine, Exception e) {
    logger.error("Status: " + status + " Exec in error with " + commandLine +
                 " returns " + e.getMessage());
    final OpenR66RunnerErrorException exc = new OpenR66RunnerErrorException(
        "<STATUS>" + status + "</STATUS><ERROR>" + e.getMessage() + "</ERROR>");
    futureCompletion.setFailure(exc);
  }
}
