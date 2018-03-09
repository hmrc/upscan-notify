# upscan-notify

Microservice for notifying services that requested upload of files created externally to HMRC estate, namely from members of the public.
This is not intended to be used for transfer of files from one HMRC service to another, for this you need to intergrate
directly with the file transfer service.

[![Build Status](https://travis-ci.org/hmrc/upscan-notify.svg)](https://travis-ci.org/hmrc/upscan-notify) [ ![Download](https://api.bintray.com/packages/hmrc/releases/upscan-notify/images/download.svg) ](https://bintray.com/hmrc/releases/upscan-notify/_latestVersion)

# Running locally

In order to run the service against one of HMRC accounts (labs, live) it's needed to have an AWS accounts with proper
role. See [UpScan Accounts/roles](https://github.tools.tax.service.gov.uk/HMRC/aws-users/blob/master/AccountLinks.md)
for proper details.

Prerequisites:
- AWS accounts with proper roles setup
- Proper AWS credential configuration set up according to this document [aws-credential-configuration](https://github.tools.tax.service.gov.uk/HMRC/aws-users), with the credentials below:
```
[upscan-service-prototypes-engineer]
source_profile = webops-users
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
output = json
region = eu-west-2
mfa_serial = arn:aws:iam::638924580364:mfa/your.username
role_arn = arn:aws:iam::063874132475:role/RoleServiceUpscanEngineer

[webops-users]
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
mfa_serial = arn:aws:iam::638924580364:mfa/your.username
region = eu-west-2
role_arn = arn:aws:iam::063874132475:role/RoleServiceUpscanEngineer
```
- Working AWS MFA authentication
- Have python 2.7 installed
- Install botocore and awscli python modules locally:
  - For Linux:
```
sudo pip install botocore
sudo pip install awscli
```
  - For Mac (Mac has issues with pre-installed version of ```six``` as discussed [here](https://github.com/pypa/pip/issues/3165):
```
sudo pip install botocore --ignore-installed six
sudo pip install awscli --ignore-installed six
```

In order to run the app against lab environment it's neeeded to run the following commands:
```
export AWS_DEFAULT_PROFILE=name_of_proper_profile_in_dot_aws_credentials_file
./aws-profile sbt
```
These commands will give you an access to SBT shell where you can run the service using 'run' or 'start' commands.

### Tests

Upscan service has end-to-end acceptance tests which can be found in https://github.com/hmrc/upscan-acceptance-tests repository
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
