package org.atlasapi.media.content.schedule;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;

import org.joda.time.Interval;
import org.joda.time.LocalDate;

import com.google.common.collect.AbstractIterator;

public class IntervalDates implements Iterable<LocalDate> {

    private Interval interval;

    public IntervalDates(Interval interval) {
        this.interval = checkNotNull(interval);
    }
    
    @Override
    public Iterator<LocalDate> iterator() {
        return new AbstractIterator<LocalDate>() {
            
            private LocalDate next = interval.getStart().toLocalDate();
            private final LocalDate end = interval.getEnd().toLocalDate();
            
            @Override
            protected LocalDate computeNext() {
                LocalDate next = this.next;
                if (next.isAfter(end)) {
                    return endOfData();
                } else {
                    this.next = this.next.plusDays(1);
                }
                return next;
            }
        };
    }

}
