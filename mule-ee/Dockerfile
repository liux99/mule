FROM openjdk:8-jdk-slim

ENV MULE_VERSION 3.9.0

WORKDIR /opt
RUN useradd --user-group --shell /bin/false mule && chown mule /opt 
USER mule
RUN wget https://s3.amazonaws.com/new-mule-artifacts/mule-ee-distribution-standalone-${MULE_VERSION}.zip \
	&& unzip *.zip \
	&& ln -s mule-enterprise-standalone-${MULE_VERSION} mule && rm mule-ee-distribution-standalone-${MULE_VERSION}.zip

	# to add a license insert the following line
 # ADD ./*.lic /opt/mule-enterprise-standalone-${MULE_VERSION}/conf 
 ADD ./start.sh /opt
 # to complete the license install uncomment the next line
# RUN mule/bin/mule -installLicense mule/conf/mule-ee-license.lic && rm -f mule/conf/mule-ee-license.lic && rm -Rf examples
#
# # Define environment variables.
ENV MULE_HOME /opt/mule
#
# # Define mount points.
VOLUME ["/opt/mule/logs", "/opt/mule/conf", "/opt/mule/apps", "/opt/mule/domains"]
#
# # Define working directory.
WORKDIR /opt/mule
#
 
CMD ["/opt/mule/bin/mule" ]
#
# # Default http port
EXPOSE 8081
EXPOSE 8082
 