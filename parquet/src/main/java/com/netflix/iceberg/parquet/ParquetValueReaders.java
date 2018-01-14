/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.iceberg.parquet;

import com.google.common.collect.ImmutableList;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.Type;
import java.lang.reflect.Array;
import java.util.List;

public class ParquetValueReaders {
  public static <T> ParquetValueReader<T> option(Type type, int definitionLevel,
                                                 ParquetValueReader<T> reader) {
    if (type.isRepetition(Type.Repetition.OPTIONAL)) {
      return new OptionReader<>(definitionLevel-1, reader);
    }
    return reader;
  }

  public abstract static class PrimitiveReader<T> implements ParquetValueReader<T> {
    private final ColumnDescriptor desc;
    protected final ColumnIterator<?> column;
    private final List<TripleIterator<?>> children;

    protected PrimitiveReader(ColumnDescriptor desc) {
      this.desc = desc;
      this.column = ColumnIterator.newIterator(desc, "");
      this.children = ImmutableList.of(column);
    }

    @Override
    public void setPageSource(PageReadStore pageStore) {
      column.setPageSource(pageStore.getPageReader(desc));
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }
  }

  public static class UnboxedReader<T> extends PrimitiveReader<T> {
    public UnboxedReader(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read() {
      return (T) column.next();
    }

    public boolean readBoolean() {
      return column.nextBoolean();
    }

    public int readInteger() {
      return column.nextInteger();
    }

    public long readLong() {
      return column.nextLong();
    }

    public float readFloat() {
      return column.nextFloat();
    }

    public double readDouble() {
      return column.nextDouble();
    }

    public Binary readBinary() {
      return column.nextBinary();
    }
  }

  private static class OptionReader<T> implements ParquetValueReader<T> {
    private final int definitionLevel;
    private final ParquetValueReader<T> reader;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    OptionReader(int definitionLevel, ParquetValueReader<T> reader) {
      this.definitionLevel = definitionLevel;
      this.reader = reader;
      this.column = reader.column();
      this.children = reader.columns();
    }

    @Override
    public void setPageSource(PageReadStore pageStore) {
      reader.setPageSource(pageStore);
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public T read() {
      if (column.currentDefinitionLevel() > definitionLevel) {
        return reader.read();
      }

      for (TripleIterator<?> column : children) {
        column.nextNull();
      }

      return null;
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }
  }

  public abstract static class RepeatedReader<T, I, E> implements ParquetValueReader<T> {
    private final int definitionLevel;
    private final int repetitionLevel;
    private final ParquetValueReader<E> reader;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    protected RepeatedReader(int definitionLevel, int repetitionLevel, ParquetValueReader<E> reader) {
      this.definitionLevel = definitionLevel;
      this.repetitionLevel = repetitionLevel;
      this.reader = reader;
      this.column = reader.column();
      this.children = reader.columns();
    }

    @Override
    public void setPageSource(PageReadStore pageStore) {
      reader.setPageSource(pageStore);
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public T read() {
      I intermediate = newListData();

      do {
        if (column.currentDefinitionLevel() > definitionLevel) {
          addElement(intermediate, reader.read());
        } else {
          // consume the empty list triple
          for (TripleIterator<?> column : children) {
            column.nextNull();
          }
          // if the current definition level is equal to the definition level of this repeated type,
          // then the result is an empty list and the repetition level will always be <= rl.
          break;
        }
      } while (column.currentRepetitionLevel() > repetitionLevel);

      return buildList(intermediate);
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    protected abstract I newListData();

    protected abstract void addElement(I list, E element);

    protected abstract T buildList(I list);
  }

  public abstract static class RepeatedKeyValueReader<M, I, K, V> implements ParquetValueReader<M> {
    private final int definitionLevel;
    private final int repetitionLevel;
    private final ParquetValueReader<K> keyReader;
    private final ParquetValueReader<V> valueReader;
    private final TripleIterator<?> column;
    private final List<TripleIterator<?>> children;

    protected RepeatedKeyValueReader(int definitionLevel, int repetitionLevel,
                           ParquetValueReader<K> keyReader, ParquetValueReader<V> valueReader) {
      this.definitionLevel = definitionLevel;
      this.repetitionLevel = repetitionLevel;
      this.keyReader = keyReader;
      this.valueReader = valueReader;
      this.column = keyReader.column();
      this.children = ImmutableList.<TripleIterator<?>>builder()
          .addAll(keyReader.columns())
          .addAll(valueReader.columns())
          .build();
    }

    @Override
    public void setPageSource(PageReadStore pageStore) {
      keyReader.setPageSource(pageStore);
      valueReader.setPageSource(pageStore);
    }

    @Override
    public TripleIterator<?> column() {
      return column;
    }

    @Override
    public M read() {
      I intermediate = newMapData();

      do {
        if (column.currentDefinitionLevel() > definitionLevel) {
          addPair(intermediate, keyReader.read(), valueReader.read());
        } else {
          // consume the empty map triple
          for (TripleIterator<?> column : children) {
            column.nextNull();
          }
          // if the current definition level is equal to the definition level of this repeated type,
          // then the result is an empty list and the repetition level will always be <= rl.
          break;
        }
      } while (column.currentRepetitionLevel() > repetitionLevel);

      return buildMap(intermediate);
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    protected abstract I newMapData();

    protected abstract void addPair(I map, K key, V value);

    protected abstract M buildMap(I map);
  }

  public abstract static class StructReader<T, I> implements ParquetValueReader<T> {
    private interface Setter<R> {
      void set(R record, int pos);
    }

    private final GroupType type;
    private final int definitionLevel;
    private final ParquetValueReader<?>[] readers;
    private final TripleIterator<?>[] columns;
    private final Setter<I>[] setters;
    private final List<TripleIterator<?>> children;

    @SuppressWarnings("unchecked")
    protected StructReader(GroupType type, int definitionLevel, List<ParquetValueReader<?>> readers) {
      this.type = type;
      this.definitionLevel = definitionLevel;
      this.readers = (ParquetValueReader<?>[]) Array.newInstance(
          ParquetValueReader.class, readers.size());
      this.columns = (TripleIterator<?>[]) Array.newInstance(TripleIterator.class, readers.size());
      this.setters = (Setter<I>[]) Array.newInstance(Setter.class, readers.size());

      ImmutableList.Builder<TripleIterator<?>> columnsBuilder = ImmutableList.builder();
      for (int i = 0; i < readers.size(); i += 1) {
        ParquetValueReader<?> reader = readers.get(i);
        this.readers[i] = readers.get(i);
        this.columns[i] = reader.column();
        this.setters[i] = newSetter(reader, type.getType(i));
        columnsBuilder.addAll(reader.columns());
      }

      this.children = columnsBuilder.build();
    }

    @Override
    public final void setPageSource(PageReadStore pageStore) {
      for (int i = 0; i < readers.length; i += 1) {
        readers[i].setPageSource(pageStore);
      }
    }

    @Override
    public final TripleIterator<?> column() {
      return columns[0];
    }

    @Override
    public final T read() {
      I intermediate = newStructData(type);

      for (int i = 0; i < readers.length; i += 1) {
        if (!type.getType(i).isRepetition(Type.Repetition.OPTIONAL) ||
            columns[i].currentDefinitionLevel() > definitionLevel) {
          set(intermediate, i, readers[i].read());
          //setters[i].set(intermediate, i);
        } else {
          setNull(intermediate, i);
          for (TripleIterator<?> column : readers[i].columns()) {
            column.nextNull();
          }
        }
      }

      return buildStruct(intermediate);
    }

    @Override
    public List<TripleIterator<?>> columns() {
      return children;
    }

    private Setter<I> newSetter(ParquetValueReader<?> reader, Type type) {
      if (reader instanceof UnboxedReader && type.isPrimitive()) {
        UnboxedReader<?> unboxed  = (UnboxedReader<?>) reader;
        switch (type.asPrimitiveType().getPrimitiveTypeName()) {
          case BOOLEAN:
            return (record, pos) -> setBoolean(record, pos, unboxed.readBoolean());
          case INT32:
            return (record, pos) -> setInteger(record, pos, unboxed.readInteger());
          case INT64:
            return (record, pos) -> setLong(record, pos, unboxed.readLong());
          case FLOAT:
            return (record, pos) -> setFloat(record, pos, unboxed.readFloat());
          case DOUBLE:
            return (record, pos) -> setDouble(record, pos, unboxed.readDouble());
          case FIXED_LEN_BYTE_ARRAY:
          case BINARY:
            return (record, pos) -> set(record, pos, unboxed.readBinary());
          default:
            throw new UnsupportedOperationException("Unsupported type: " + type);
        }
      }

      return (record, pos) -> set(record, pos, reader.read());
    }

    protected abstract I newStructData(GroupType type);

    protected abstract T buildStruct(I struct);

    /**
     * Used to set a struct value by position.
     * <p>
     * To avoid boxing, override {@link #setInteger(Object, int, int)} and similar methods.
     *
     * @param struct a struct object created by {@link #newStructData(GroupType)}
     * @param pos the position in the struct to set
     * @param value the value to set
     */
    protected abstract void set(I struct, int pos, Object value);

    protected void setNull(I struct, int pos) {
      set(struct, pos, null);
    }

    protected void setBoolean(I struct, int pos, boolean value) {
      set(struct, pos, value);
    }

    protected void setInteger(I struct, int pos, int value) {
      set(struct, pos, value);
    }

    protected void setLong(I struct, int pos, long value) {
      set(struct, pos, value);
    }

    protected void setFloat(I struct, int pos, float value) {
      set(struct, pos, value);
    }

    protected void setDouble(I struct, int pos, double value) {
      set(struct, pos, value);
    }
  }
}
