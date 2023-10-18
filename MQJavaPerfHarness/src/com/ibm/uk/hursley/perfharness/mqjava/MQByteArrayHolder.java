/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/

package com.ibm.uk.hursley.perfharness.mqjava;

/**
 * Wrapper class to allow MQ correlIDs (24 bytes) to be used as keys in a ConcurrentHashMap.
 */
public class MQByteArrayHolder 
{
	public byte [] data;
	public int dataHashCode = -1;

	public MQByteArrayHolder(int capacity)
	{
		data = new byte[capacity];
	}
	public void setData(byte[] b)
	{
		System.arraycopy(b, 0, data, 0, b.length); 
		dataHashCode = java.util.Arrays.hashCode(data);
	}

	@Override
	public String toString()
	{
		return getHexString(data);
	}
		
	@Override
	public int hashCode() 
	{
		return dataHashCode;
	}

	@Override
    public boolean equals(Object other)
	{
		boolean retval = false;
		if ( other instanceof MQByteArrayHolder )
		{
			MQByteArrayHolder bah = (MQByteArrayHolder)other;
			retval = java.util.Arrays.equals(data, bah.data);
		}
		return retval;
	}
    
    public static String getHexString(byte[] b) {
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

    /**
     * Used for unit testing only - this class is not intended to be run standalone
     * during normal operation! The repo in general should probably use JUnit and 
     * have better unit testing, but until it does we will use this approach.
     * 
     * java -cp /home/tdolby/github.com/perf-harness/PerfHarness/build/perfharness.jar com.ibm.uk.hursley.perfharness.mqjava.MQByteArrayHolder
     */
	public static void main(String[] args) throws Exception
    {
        byte [] msgIdOne = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
        byte [] msgIdTwo = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2 };
        
        System.out.println("Checking basic capabilities");
        {
            MQByteArrayHolder bah = new MQByteArrayHolder(24);
            bah.setData(msgIdOne);

            // Not sure this one is true in all cases . . . 
            if ( bah.hashCode() == 0  ) throw new Exception("Hash code should not be zero for a non-zero byte array");
            if ( !bah.toString().equals("000000000000000000000000000000000000000000000001") ) throw new Exception("Incorrect string: "+bah.toString());
        }
        System.out.println("Checking handling for same byte arrays");
        {
            MQByteArrayHolder bahOne = new MQByteArrayHolder(24);
            MQByteArrayHolder bahTwo = new MQByteArrayHolder(24);
            bahOne.setData(msgIdOne);
            bahTwo.setData(msgIdOne);

            // Need assertEquals!
            if ( bahOne.hashCode() != bahTwo.hashCode() ) throw new Exception("Hash codes for same byte arrays should be the same");
            if ( !bahOne.toString().equals(bahTwo.toString()) ) throw new Exception("Strings for same byte arrays should be the same");
            if ( !bahOne.equals(bahTwo) ) throw new Exception("equals() for same byte arrays should be true");
        }
        System.out.println("Checking handling for different byte arrays");
        {
            MQByteArrayHolder bahOne = new MQByteArrayHolder(24);
            MQByteArrayHolder bahTwo = new MQByteArrayHolder(24);
            bahOne.setData(msgIdOne);
            bahTwo.setData(msgIdTwo);

            // Need assertNotEquals!
            if ( bahOne.hashCode() == bahTwo.hashCode() ) throw new Exception("Hash codes for different byte arrays should be different");
            if ( bahOne.toString().equals(bahTwo.toString()) ) throw new Exception("Strings for different byte arrays should be different");
            if ( bahOne.equals(bahTwo) ) throw new Exception("equals() for different byte arrays should be false");
        }
        
        System.out.println("Checking handling for byte arrays after reset");
        {
            MQByteArrayHolder bahOne = new MQByteArrayHolder(24);
            MQByteArrayHolder bahTwo = new MQByteArrayHolder(24);
            bahOne.setData(msgIdOne);
            bahTwo.setData(msgIdOne);
            bahTwo.setData(msgIdTwo);

            // Need assertNotEquals!
            if ( bahOne.hashCode() == bahTwo.hashCode() ) throw new Exception("Hash codes for different byte arrays should be different");
            if ( bahOne.toString().equals(bahTwo.toString()) ) throw new Exception("Strings for different byte arrays should be different");
            if ( bahOne.equals(bahTwo) ) throw new Exception("equals() for different byte arrays should be false");
        }
        // We could add more tests, but this class is only intended to be used in a ConcurrentHashMap so we don't need much more right now.
        System.out.println("All tests passed");
    }
}
