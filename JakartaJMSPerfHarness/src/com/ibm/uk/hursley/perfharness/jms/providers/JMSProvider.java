/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
 ********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms.providers;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.QueueConnection;
import jakarta.jms.QueueConnectionFactory;
import jakarta.jms.QueueSession;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicConnectionFactory;
import jakarta.jms.TopicSession;
import javax.naming.NamingException;

import com.ibm.uk.hursley.perfharness.WorkerThread;
import com.ibm.uk.hursley.perfharness.jms.DestinationWrapper;

public interface JMSProvider {

	public TopicConnectionFactory lookupTopicConnectionFactory(String name)
			throws JMSException, NamingException;

	public QueueConnectionFactory lookupQueueConnectionFactory(String name)
			throws JMSException, NamingException;

	public ConnectionFactory lookupConnectionFactory(String name)
			throws JMSException, NamingException;

	public DestinationWrapper<Queue> lookupQueue(String queue, QueueSession session)
			throws JMSException, NamingException;

	public DestinationWrapper<Topic> lookupTopic(String topic, TopicSession session)
			throws JMSException, NamingException;

	public DestinationWrapper<Queue> lookupQueue(String queue, Session session)
			throws JMSException, NamingException;

	public DestinationWrapper<Topic> lookupTopic(String topic, Session session)
			throws JMSException, NamingException;

	public DestinationWrapper<Destination> lookupDestination(String uri, Session session)
			throws JMSException, NamingException;

	public QueueConnection getQueueConnection(QueueConnectionFactory qcf)
			throws JMSException;

	//JMS 2.0
	public DestinationWrapper<Queue> lookupQueue(String queue, JMSContext session) throws JMSException, NamingException;

	public DestinationWrapper<Topic> lookupTopic(String topic, JMSContext session) throws JMSException, NamingException;

	
	/**
	 * 
	 * @param tcf
	 * @param uniqueID
	 *            If not null, this value is unique within the current JVM. It
	 *            may be used to help build a clientID.
	 * @throws JMSException
	 */
	public TopicConnection getTopicConnection(TopicConnectionFactory tcf, String uniqueID)
			throws JMSException;

	/**
	 * 
	 * @param cf
	 * @param worker if not null, this is used a spart of the reconnect ability
	 * @param uniqueID
	 *            If not null, this value is unique within the current JVM. It
	 *            may be used to help build a clientID.
	 * @throws JMSException
	 * @throws InterruptedException
	 */
	public Connection getConnection(ConnectionFactory cf, WorkerThread worker, String uniqueID) throws JMSException, InterruptedException;
	
	public void createQueue(String name) throws Exception;

	public void createTopic(String name) throws Exception;
	
	public void createConnectionFactory(String name) throws Exception;

    public void createQueueConnectionFactory(String name) throws Exception;

    public void createTopicConnectionFactory(String name) throws Exception;
    
    /**
     ** delete a queue on the provider. differs from deleting a jms queue.
     ** unbind the associated admin object from the jndi store. 
     */
    public void deleteQueue(String name) throws Exception;
    
    /**
     ** delete a topic on the provider. differs from deleting a jms topic.
     ** unbind the associated admin object from the jndi store. 
     */
    
    public void deleteTopic(String name) throws Exception;
    
    /**
     ** unbind an admin object from the jndi store.
     */
    public void unbind(String lookupName) throws Exception;
    
    /**
     ** Process any required setup initialization.
     */
    public void initializeSetup() throws Exception;
    
    /**
     ** Perform any processing required to complete setup.
     */
    public void completeSetup() throws Exception;
    
    /**
	 * Some providers implement both Queue and Topic on the same object leaving
	 * generic JMS unable to identify the difference. It is expected most
	 * providers will be happy with the default implementation.
	 * 
	 * @return if the given Destination object is a queue. It is also assumed
	 *         that if not, it must be a topic.
	 */
    public boolean isQueue( Destination destination );
    
}
