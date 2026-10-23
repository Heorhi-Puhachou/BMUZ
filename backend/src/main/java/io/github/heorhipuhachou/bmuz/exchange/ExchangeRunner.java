package io.github.heorhipuhachou.bmuz.exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Падчас запуску робіць імпарт або экспарт, калі гэтага патрабуе ўласьцівасьць:
 * {@code --bmuz.import.by-txt=FILE}, {@code --bmuz.import.json=FILE}, {@code --bmuz.export.json=FILE}.
 * Спачатку выконваюцца імпарты, таму адзін запуск можа імпартаваць BY.txt і экспартаваць вынік.
 */
@Component
class ExchangeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRunner.class);

    private final CatalogueImporter importer;
    private final CatalogueExporter exporter;
    private final Environment env;

    ExchangeRunner(CatalogueImporter importer, CatalogueExporter exporter, Environment env) {
        this.importer = importer;
        this.exporter = exporter;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String byTxt = env.getProperty("bmuz.import.by-txt");
        if (byTxt != null) {
            log.info("Imported {}: {}", byTxt, importer.importByTxt(Path.of(byTxt)));
        }
        String jsonIn = env.getProperty("bmuz.import.json");
        if (jsonIn != null) {
            log.info("Imported {}: {}", jsonIn, importer.importJson(Path.of(jsonIn)));
        }
        String jsonOut = env.getProperty("bmuz.export.json");
        if (jsonOut != null) {
            exporter.exportTo(Path.of(jsonOut));
            log.info("Exported the catalogue to {}", jsonOut);
        }
    }
}
