package mediaextractor.image.resolvers;

import mediaextractor.image.ImageResolver;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class OpengraphImageResolver implements ImageResolver {

    private List<Tag> tags = Arrays.asList(
            // New Facebook
            new Tag("facebook", "property", "og:image", "content"),
            // Old Facebook
            new Tag("facebook", "rel", "image_src", "href"),
            // Old Twitter
            new Tag("twitter", "name", "twitter:image", "value"),
            // New Twitter
            new Tag("twitter", "name", "twitter:image", "content")
    );

    @Override
    public Optional<String> mainImage(final String url, final String html) {
        Document document = Jsoup.parse(html, url);
        Stream<Element> meta = Stream.concat(
                document.getElementsByTag("meta").stream(),
                document.getElementsByTag("link").stream()
        );

        List<ImageSrcWithScore> images = meta.flatMap(m ->
                tags.stream()
                        .filter(tag -> m.hasAttr(tag.attribute) && Objects.equals(m.attr(tag.attribute), tag.name) && m.hasAttr(tag.value))
                        .map(tag -> new SimpleEntry<>(m, tag))
        )
                .map(entry -> new ImageSrcWithScore(entry.getKey().attr(entry.getValue().value), entry.getValue().type, 0))
                .collect(toList());

        Optional<String> mainImage;
        if (images.size() == 1) {
            mainImage = Optional.of(images.get(0).src);
        } else {
            mainImage = images.stream()
                    .map(img -> {
                        if (Pattern.compile("(large|big)").matcher(img.src).find()) {
                            img.incrementScore();
                        }
                        if (Objects.equals(img.type, "twitter")) {
                            img.incrementScore();
                        }
                        return img;
                    })
                    .sorted(Comparator.comparingInt(img -> img.score))
                    .findFirst()
                    .map(img -> img.src);
        }
        return mainImage
                .map(src -> {
                    try {
                        return src.matches("^http") ? src : new URI(url).resolve(src).toString();
                    } catch (URISyntaxException e) {
                        return null;
                    }
                });
    }

    public static class Tag {
        public final String type;
        public final String attribute;
        public final String name;
        public final String value;

        public Tag(String type, String attribute, String name, String value) {
            this.type = type;
            this.attribute = attribute;
            this.name = name;
            this.value = value;
        }
    }

    private static class ImageSrcWithScore {
        private final String src;
        private final String type;
        private int score;

        public ImageSrcWithScore(String src, String type, int score) {
            this.src = src;
            this.type = type;
            this.score = score;
        }

        public ImageSrcWithScore incrementScore() {
            this.score++;
            return this;
        }
    }

}

