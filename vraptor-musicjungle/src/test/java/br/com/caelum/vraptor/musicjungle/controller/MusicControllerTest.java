package br.com.caelum.vraptor.musicjungle.controller;

import static br.com.caelum.vraptor.musicjungle.serialization.XStreamBuilderFactory.cleanInstance;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import br.com.caelum.vraptor.musicjungle.dao.MusicDao;
import br.com.caelum.vraptor.musicjungle.enums.MusicType;
import br.com.caelum.vraptor.musicjungle.files.Musics;
import br.com.caelum.vraptor.musicjungle.interceptor.UserInfo;
import br.com.caelum.vraptor.musicjungle.model.Music;
import br.com.caelum.vraptor.musicjungle.model.MusicOwner;
import br.com.caelum.vraptor.musicjungle.model.User;
import br.com.caelum.vraptor.musicjungle.util.model.MusicBuilder;
import br.com.caelum.vraptor.musicjungle.util.model.UserBuilder;
import br.com.caelum.vraptor.observer.upload.UploadedFile;
import br.com.caelum.vraptor.proxy.JavassistProxifier;
import br.com.caelum.vraptor.serialization.gson.GsonBuilderWrapper;
import br.com.caelum.vraptor.util.test.MockHttpResult;
import br.com.caelum.vraptor.util.test.MockInstanceImpl;
import br.com.caelum.vraptor.util.test.MockSerializationResult;
import br.com.caelum.vraptor.util.test.MockValidator;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public class MusicControllerTest {

	private MockSerializationResult result;
	private MockValidator validator;
	@Mock
	private UserInfo userInfo;
	@Mock
	private MusicDao dao;
	@Mock
	private Musics musics;
	private MusicController controller;
	
	private Music music;
	private User user;

	@Mock
	private UploadedFile uploadFile;
	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		
		List<JsonSerializer<?>> jsonSerializers = new ArrayList<>();
		List<JsonDeserializer<?>> jsonDeserializers = new ArrayList<>();
		
		result = new MockSerializationResult(new JavassistProxifier(), cleanInstance(),
				new GsonBuilderWrapper(new MockInstanceImpl<>(jsonSerializers), new MockInstanceImpl<>(jsonDeserializers)));
		
		validator = new MockValidator();
		controller = new MusicController(dao, userInfo, result, validator, musics);
		
		music = new MusicBuilder().withId(1L).withType(MusicType.ROCK).withTitle("Some Music").withDescription("Some description");
		user = new UserBuilder().withName("Renan").withLogin("renanigt").withPassword("1234");
	}
	
	@Test
	public void shouldAddMusic() {
		when(userInfo.getUser()).thenReturn(user);

		controller.add(music, uploadFile);
		
		verify(dao).add(music);
		verify(dao).add(any(MusicOwner.class));
		verify(musics).save(uploadFile, music);

		assertThat(result.included().get("notice").toString(), is(music.getTitle() + " music added"));
	}

	@Test
	public void shouldShowMusicWhenExists() {
		when(dao.load(music)).thenReturn(music);
		
		controller.show(music);
		
		assertNotNull(result.included().get("music"));
		assertThat((Music) result.included().get("music"), is(music));
	}
	
	@Test
	public void shouldNotShowMusicWhenDoesNotExists() {
		when(dao.load(music)).thenReturn(null);
		
		controller.show(music);
		
		assertNull(result.included().get("music"));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void shouldReturnMusicList() {
		when(dao.searchSimilarTitle(music.getTitle())).thenReturn(Arrays.asList(music));
		controller.search(music);
		assertThat((List<Music>) result.included().get("musics"), contains(music));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void shouldReturnEmptyList() {
		when(dao.searchSimilarTitle(music.getTitle())).thenReturn(new ArrayList<Music>());
		controller.search(music);
		List<Music> musics = (List<Music>) result.included().get("musics");
		assertThat(musics, empty());
	}
	
	@Test
	public void shouldNotDownloadMusicWhenDoesNotExist() {
		when(dao.load(music)).thenReturn(music);
		when(musics.getFile(music)).thenReturn(new File("/tmp/uploads/Music_" + music.getId() + ".mp3"));
		try {
			controller.download(music);
		} catch (FileNotFoundException e) {
			verify(musics).getFile(music);
		}
	}
	
	@Test
	public void shouldShowAllMusicsAsJSON() throws Exception {
		when(dao.listAll()).thenReturn(Arrays.asList(music));
		controller.showAllMusicsAsJSON();
		assertThat(result.serializedResult(), is("{\"list\":[{\"id\":1,\"title\":\"Some Music\"," +
				"\"description\":\"Some description\",\"type\":\"ROCK\"}]}"));
	}
	
	@Test
	public void shouldShowAllMusicsAsXML() throws Exception {
		when(dao.listAll()).thenReturn(Arrays.asList(music));
		controller.showAllMusicsAsXML();
		assertThat(result.serializedResult(), is("<list>\n" +
												 "  <musicBuilder>\n" +
												 "    <id>1</id>\n" +
												 "    <title>Some Music</title>\n" +
												 "    <description>Some description</description>\n" +
												 "    <type>ROCK</type>\n" +
												 "  </musicBuilder>\n" +
												 "</list>"));
	}
	
	@Test
	public void shouldShowAllMusicsAsHTTP() {
		MockHttpResult mockHttpResult = new MockHttpResult();
		controller = new MusicController(dao, userInfo, mockHttpResult, validator, musics);
		when(dao.listAll()).thenReturn(Arrays.asList(music));
		controller.showAllMusicsAsHTTP();
		assertThat(mockHttpResult.getBody(), is("<p class=\"content\">"+ Arrays.asList(music).toString()+"</p>"));
	}
	
	@Test
	public void shouldListAs() throws Exception {
		when(dao.listAll()).thenReturn(Arrays.asList(music));
		controller.listAs();
		assertThat(result.serializedResult(), is("<list>\n" +
				"  <musicBuilder>\n" +
				"    <id>1</id>\n" +
				"    <title>Some Music</title>\n" +
				"    <description>Some description</description>\n" +
				"    <type>ROCK</type>\n" +
				"  </musicBuilder>\n" +
				"</list>"));
	}
	
}
