package ch.elexis.core.services.eenv;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.junit.Test;

import ch.elexis.core.eenv.AccessToken;
import ch.elexis.core.services.oauth2.AccessTokenUtil;
import ch.elexis.core.services.oauth2.KeycloakAccessTokenResponse;

public class KeycloakAccessTokenTest {

	@Test
	public void loadKeycloakAccessToken() {

		String accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJENUszcFlpRFh4azUtTHMzYzNQYmhUM1ZxS0xkXzlIbExmRmktbGNSMzBzIn0.eyJleHAiOjE2NTQ2NzY4MjksImlhdCI6MTY1NDY3MzIyOSwianRpIjoiNTliYmNkN2UtMzRhZS00ZWQ0LTgyNGMtOWZiYTdjNjI0ZDdkIiwiaXNzIjoiaHR0cHM6Ly9tYXJjb3MtbWJwLTIwMTkubXllbGV4aXMuY2gva2V5Y2xvYWsvYXV0aC9yZWFsbXMvRWxleGlzRW52aXJvbm1lbnQiLCJhdWQiOlsic29sciIsImFjY291bnQiXSwic3ViIjoiMTQ0YmRlYWItOWJiZi00N2Q2LTllYzQtNTU5ODJjZDIwZTgyIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoicG9zdG1hbiIsInNlc3Npb25fc3RhdGUiOiJhMmZmOTA1NC1iZTU5LTQ3NjUtYjg4Zi0zZjNkZjM5ZTE0MTQiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJ1c2VyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsic29sciI6eyJyb2xlcyI6WyJ1c2VyIl19LCJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6InByb2ZpbGUgZW1haWwiLCJzaWQiOiJhMmZmOTA1NC1iZTU5LTQ3NjUtYjg4Zi0zZjNkZjM5ZTE0MTQiLCJzb2xyLXJvbGVzIjpbInVzZXIiXSwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJNYXJjbyBEZXNjaGVyIiwicHJlZmVycmVkX3VzZXJuYW1lIjoibWFyY28iLCJnaXZlbl9uYW1lIjoiTWFyY28iLCJmYW1pbHlfbmFtZSI6IkRlc2NoZXIiLCJlbWFpbCI6ImRlc2NoZXJAbWVkZXZpdC5hdCJ9.GsUhtah3gTjw6e_7PhNspbX689zbvwL1Cbiq8rxskXxwlqJ9aXWC3mv-szry3L43kOHgX4agqqyJ8QysOtDpwsuQHMHqxbh-c12VFjQkoEcaHIKUSIknV5CvrUAY9AFbz0dy8FvU7SXoDnHUns8tJYlbCj643940dTGBNjkfxchvlIEFn7Xj7rPEWsrNZ8WgBKvPhN2sQblrJQ9O4_NTDy40anERExfnGTtzNhrJDNMDXQxrrBt6U52QTD-dDeWlGHsQHUoKc5_-TZe-h6AfDKxPINXHpmJX3jHBFJo57rivczlLSleaeKwz0X23nl9QIKEVMqgcrag8Vs9-aj8y-w";
		KeycloakAccessTokenResponse accessTokenResponse = new KeycloakAccessTokenResponse();
		accessTokenResponse.setToken(accessToken);
		AccessToken keycloakAccessToken = AccessTokenUtil.load(accessTokenResponse);

		assertEquals(accessToken, keycloakAccessToken.getToken());
		Date expirationTime = new Date(1654673229 * 1000);
		assertEquals(expirationTime, keycloakAccessToken.getAccessTokenExpiration());
	}

}
