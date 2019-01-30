import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicSessionCredentials}
import com.amazonaws.regions.Regions
import com.amazonaws.services.identitymanagement.model._
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagement, AmazonIdentityManagementClientBuilder}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.securitytoken.model.{AssumeRoleRequest, AssumeRoleResult, Credentials}
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}

object AwsS3Integration extends App {
  val userName = "Bob"
  val role = getRole("temp-role")
  generateSts(role, userName)

  def generateSts(role: Role, userName: String) : Credentials = {
    val client: AWSSecurityTokenService = AWSSecurityTokenServiceClientBuilder.standard().withRegion(Regions.US_WEST_2).build()

    val policy: String =
      s"""{
         |	"Version": "2012-10-17",
         |	"Statement": [
         |		{
         |			"Sid": "AllowListingOfUserFolder",
         |			"Action": ["s3:ListBucket"],
         |			"Effect": "Allow",
         |			"Resource": ["arn:aws:s3:::ayushi-test"],
         |			"Condition": {
         |				"StringLike": {
         |					"s3:prefix": [
         |						"$userName/*",
         |						"$userName"
         |					]
         |				}
         |			}
         |		},
         |		{
         |			"Sid": "AllowAllS3ActionsInUserFolder",
         |			"Action": ["s3:*"],
         |			"Effect": "Allow",
         |			"Resource": ["arn:aws:s3:::ayushi-test/$userName*"]
         |		}
         |	]
         |}""".stripMargin

    val request: AssumeRoleRequest = new AssumeRoleRequest()
      .withRoleSessionName(userName)
      .withRoleArn(role.getArn)
      .withPolicy(policy)
      .withDurationSeconds(43200)
      .withRoleArn(role.getArn)
    val response: AssumeRoleResult = client.assumeRole(request)
    val tempCredentials: Credentials = response.getCredentials
    println(" Credentials are " + tempCredentials.getAccessKeyId + "  " + tempCredentials.getExpiration + " " + tempCredentials.getSessionToken)


    val basicSessionCredentials = new BasicSessionCredentials(tempCredentials.getAccessKeyId, tempCredentials.getSecretAccessKey, tempCredentials.getSessionToken)
    val s3Client = AmazonS3ClientBuilder.standard.withCredentials(new AWSStaticCredentialsProvider(basicSessionCredentials)).withRegion(Regions.US_WEST_2).build

    /* This code is for testing temporary credentials to perform operation on S3 Bucket
    and check whether session level policy applies or not.
    List Objects in different Users Album should'nt happen
    We get com.amazonaws.services.s3.model.AmazonS3Exception: Access Denied (Service: Amazon S3; Status Code: 403
    Otherwise for bob we get List(Bob/, Bob/testing)
    */
    /*
        val listObjectsRequest = new ListObjectsRequest().withBucketName("ayushi-test").withPrefix("Bob/").withDelimiter("/")
        val objects = s3Client.listObjects(listObjectsRequest)

        val list = for {
          summary <- objects.getObjectSummaries.asScala.toList
        } yield {
          summary.getKey
        }
        println(list)
        // Deleting Objects Recursively through the objects listed by list operation above

        val DeleteObjectsRequest = new DeleteObjectsRequest("ayushi-test")
        s3Client.deleteObjects(DeleteObjectsRequest.withBucketName("ayushi-test").withKeys(list: _*))

        //Testing getObject Operation on user folder
        import com.amazonaws.services.s3.model.ListObjectsV2Request
        import com.amazonaws.services.s3.model.ListObjectsV2Result
        import com.amazonaws.services.s3.model.S3ObjectSummary
        val req = new ListObjectsV2Request().withBucketName("ayushi-test").withPrefix("Bob/").withDelimiter("/")
        val listing = s3Client.listObjectsV2(req)
       import scala.collection.JavaConversions._
        for (commonPrefix <- listing.getCommonPrefixes) {
          System.out.println(commonPrefix)
        }
        import scala.collection.JavaConversions._
        for (summary <- listing.getObjectSummaries) {
          System.out.println(summary.getKey)
        }
      //Counting no. of objects is not permitted to user and he cannot check other users objects also ,403 Access Denied here

        import com.amazonaws.services.s3.model.ObjectListing
        val objects = s3Client.listObjects("ayushi-test")
        System.out.println("No. of Objects: " + objects.getBucketName)
        //checking upload functionality it uploads Bob folder with a new file

        val file_path = "/home/knoldus/Downloads/testing"
        import java.nio.file.Paths
        val key_name = Paths.get(file_path).getFileName.toString
        s3Client.putObject("ayushi-test/Bob/", key_name, new File(file_path))


       //return Access Denied in this case
        val buckets = s3Client.listBuckets
        import scala.collection.JavaConversions._
        for (b <- buckets) {
          System.out.println("List of buckets names" + b.getName)
        }*/
    tempCredentials
  }

  def getRole(userName: String): Role = {
    val client: AmazonIdentityManagement = AmazonIdentityManagementClientBuilder.standard().withRegion(Regions.US_WEST_2).build()
    val request: GetRoleRequest = new GetRoleRequest().withRoleName(userName)
    val response: GetRoleResult = client.getRole(request)
    response.getRole
  }
}