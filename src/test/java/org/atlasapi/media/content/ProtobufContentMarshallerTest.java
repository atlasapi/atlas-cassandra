package org.atlasapi.media.content;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Publisher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.netflix.astyanax.ColumnListMutation;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnList;

@RunWith(MockitoJUnitRunner.class)
public class ProtobufContentMarshallerTest {

    private final ContentMarshaller marshaller = new ProtobufContentMarshaller();

    @Test
    @SuppressWarnings("unchecked")
    public void testMarshallsAndUnmarshallsContent() {

        Content content = new Episode();
        content.setId(Id.valueOf(1234));
        content.setPublisher(Publisher.BBC);
        content.setTitle("title");
        
        ColumnListMutation<String> mutation = mock(ColumnListMutation.class);
        
        marshaller.marshallInto(mutation, content);
        
        ArgumentCaptor<String> col = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<byte[]> val = ArgumentCaptor.forClass(byte[].class);
        
        verify(mutation, times(4)).putColumn(col.capture(), val.capture());
        
        assertThat(col.getAllValues().size(), is(4));
        assertThat(col.getAllValues(), hasItems("IDENTIFICATION", "DESCRIPTION","SOURCE","TYPE"));

        ColumnList<String> cols = mock(ColumnList.class);
        when(cols.size()).thenReturn(4);
        when(cols.getColumnByIndex(anyInt())).then(new Answer<Column<String>>() {
            @Override
            public Column<String> answer(InvocationOnMock invocation) throws Throwable {
                return column(val.getAllValues().get((Integer)invocation.getArguments()[0]));
            }
        });
        
        Content unmarshalled = marshaller.unmarshallCols(cols);

        assertThat(unmarshalled.getId(), is(content.getId()));
        assertThat(unmarshalled.getTitle(), is(content.getTitle()));

    }

    @SuppressWarnings("unchecked")
    private Column<String> column(byte[] bytes) {
        Column<String> mock = mock(Column.class);
        when(mock.getByteArrayValue()).thenReturn(bytes);
        return mock;
    }

}
