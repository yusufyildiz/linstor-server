package com.linbit.linstor.storage.layer.provider.utils;

import com.linbit.extproc.ExtCmd.OutputData;

public class RetryIfDeviceBusy implements Commands.RetryHandler
{
    private static final int DEFAULT_RETRY_DELAY_IN_MS = 100;
    private static final int DEFAULT_RETRY_COUNT = 3;
    private final int countMax;
    private int countCurrent;

    public RetryIfDeviceBusy()
    {
        this(DEFAULT_RETRY_COUNT);
    }

    public RetryIfDeviceBusy(int countRef)
    {
        countMax = countRef;
        countCurrent = 0;
    }

    @Override
    public boolean retry(OutputData outputData)
    {
        boolean retry = countCurrent++ < countMax;
        if (retry)
        {
            String stdErr = new String(outputData.stderrData);
            if (!stdErr.contains("busy"))
            {
                retry = false;
            }
        }
        if (retry)
        {
            try
            {
                Thread.sleep(DEFAULT_RETRY_DELAY_IN_MS);
            }
            catch (InterruptedException ignored)
            {
            }
        }
        return retry;
    }

    @Override
    public boolean skip(OutputData outData)
    {
        return false;
    }
}
