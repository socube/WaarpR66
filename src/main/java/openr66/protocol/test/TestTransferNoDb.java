/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3.0 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package openr66.protocol.test;

import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.common.logging.GgSlf4JLoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import openr66.client.DirectTransfer;
import openr66.context.ErrorCode;
import openr66.context.R66Result;
import openr66.database.DbConstant;
import openr66.database.exception.OpenR66DatabaseSqlError;
import openr66.protocol.configuration.Configuration;
import openr66.protocol.networkhandler.NetworkTransaction;
import openr66.protocol.utils.R66Future;

import org.jboss.netty.logging.InternalLoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * @author Frederic Bregier
 *
 */
public class TestTransferNoDb extends DirectTransfer {
    static int nb = 100;
    /**
     * This method could be overridden in each implementation for special argument
     * @param args
     * @param rank
     * @return the new index or 0 or less if an error occurs
     */
    protected static int getSpecialParams(String []args, int rank) {
        if (args[rank].equalsIgnoreCase("-nb")) {
            rank++;
            nb = Integer.parseInt(args[rank]);
        }
        return rank;
    }

    public TestTransferNoDb(R66Future future, String remoteHost,
            String filename, String rulename, String fileinfo, boolean isMD5, int blocksize,
            NetworkTransaction networkTransaction) {
        super(future, remoteHost, filename, rulename, fileinfo, isMD5, blocksize, networkTransaction);
    }


    public static void main(String[] args) {
        InternalLoggerFactory.setDefaultFactory(new GgSlf4JLoggerFactory(
                Level.WARN));
        if (logger == null) {
            logger = GgInternalLoggerFactory.getLogger(DirectTransfer.class);
        }
        if (! getParams(args)) {
            logger.error("Wrong initialization");
            if (DbConstant.admin != null && DbConstant.admin.isConnected) {
                try {
                    DbConstant.admin.close();
                } catch (OpenR66DatabaseSqlError e) {
                }
            }
            System.exit(1);
        }

        Configuration.configuration.pipelineInit();
        NetworkTransaction networkTransaction = new NetworkTransaction();
        try {
            ExecutorService executorService = Executors.newCachedThreadPool();

            R66Future[] arrayFuture = new R66Future[nb];
            logger.warn("Start");
            long time1 = System.currentTimeMillis();
            for (int i = 0; i < nb; i ++) {
                arrayFuture[i] = new R66Future(true);
                TestTransferNoDb transaction = new TestTransferNoDb(arrayFuture[i],
                        rhost, localFilename, rule, fileInfo, ismd5, block,
                        networkTransaction);
                executorService.execute(transaction);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
            int success = 0;
            int error = 0;
            int warn = 0;
            for (int i = 0; i < nb; i ++) {
                arrayFuture[i].awaitUninterruptibly();
                R66Result result = arrayFuture[i].getResult();
                if (arrayFuture[i].isSuccess()) {
                    if (result.runner.getStatus() == ErrorCode.Warning) {
                        warn ++;
                    } else {
                        success ++;
                    }
                } else {
                    if (result.runner != null &&
                            result.runner.getStatus() == ErrorCode.Warning) {
                        warn ++;
                    } else {
                        error ++;
                    }
                }
            }
            long time2 = System.currentTimeMillis();
            long length = 0;
            // Get the first result as testing only
            R66Result result = arrayFuture[0].getResult();
            logger.warn("Final file: " +
                    (result.file != null? result.file.toString() : "no file"));
            try {
                length = result.file != null? result.file.length() : 0L;
            } catch (CommandAbstractException e) {
            }
            long delay = time2 - time1;
            float nbs = success * 1000;
            nbs /= delay;
            float mbs = nbs * length / 1024;
            logger.error("Success: " + success + " Warning: " + warn + " Error: " +
                    error + " delay: " + delay + " NB/s: " + nbs + " KB/s: " + mbs);
            executorService.shutdown();
        } finally {
            networkTransaction.closeAll();
        }
    }

}