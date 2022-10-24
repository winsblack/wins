/* pipeline 변수 설정 */
def app

node {
    // gitlab으로부터 소스 다운하는 stage
    stage('Checkout') {
            checkout scm   
    }
 
    // mvn 툴 선언하는 stage, 필자의 경우 maven 3.6.0을 사용중
    stage('Ready'){  
        sh "echo 'Ready to build'"
        mvnHome = tool 'Maven 3.6.0'
    }

    // mvn 빌드로 jar파일을 생성하는 stage
    stage('Build'){  
        sh "echo 'Build Spring Boot Jar'"
        sh "'${mvnHome}/bin/mvn' clean package"
    }

    //sonarqube 정적분석 실행하는 stage, jenkins와 sonarqube연동을 하지 않았다면 이부분은 주석처리
    stage('Static Code Analysis') {  
        sh "'${mvnHome}/bin/mvn' clean verify sonar:sonar -Dsonar.projectName=pipeline_test -Dsonar.projectKey=pipeline_test -Dsonar.projectVersion=$BUILD_NUMBER"
    }

    //dockerfile기반 빌드하는 stage ,git소스 root에 dockerfile이 있어야한다
    stage('Build image'){   
        app = docker.build("cross9308/dockertest")
    }


    //docker image를 push하는 stage, 필자는 dockerhub에 이미지를 올렸으나 보통 private image repo를 별도 구축해서 사용하는것이 좋음
    //docker.withRegistry에 dockerhub는 앞서 설정한 dockerhub credentials의 ID이다.
    stage('Push image') {   
        docker.withRegistry('https://registry.hub.docker.com', 'dockerhub') {
            app.push("${env.BUILD_NUMBER}")
            app.push("latest")
        }
    }
    
    // kubernetes에 배포하는 stage, 배포할 yaml파일(필자의 경우 test.yaml)은 jenkinsfile과 마찬가지로 git소스 root에 위치시킨다.
    // kubeconfigID에는 앞서 설정한 Kubernetes Credentials를 입력하고 'sh'는 쿠버네티스 클러스터에 원격으로 실행시킬 명령어를 기술한다.
    stage('Kubernetes deploy') {
        kubernetesDeploy configs: "test.yaml", kubeconfigId: 'Kuberconfig'
        sh "/usr/local/bin/kubectl --kubeconfig=/u01/kube-config.yaml rollout restart deployment/test-deployment -n zuno"
    }

    stage('Complete') {
        sh "echo 'The end'"
    }
}
