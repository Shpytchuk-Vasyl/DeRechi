package org.shpytchuk.ingest.source.facebook;

import java.util.List;

record GroupFeed(String name, List<FacebookStory> stories) {
}
