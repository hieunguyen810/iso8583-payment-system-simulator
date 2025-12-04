mvn clean verify sonar:sonar \
  -Dsonar.host.url=http://10.43.127.103:9000/ \
  -Dsonar.projectKey=iso8583 \
  -Dsonar.projectName='iso8583' \
  -Dsonar.token=''