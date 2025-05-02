(defproject youtube-comments "0.1.0-SNAPSHOT"
  :description "API para buscar e filtrar comentários do YouTube"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.6.0"]
                 [clj-http "3.12.3"]
                 [org.clojure/data.json "2.4.0"]
                 [cheshire "5.11.0"]  ; Adicionado para processamento JSON robusto
                 [ring/ring-core "1.9.5"]
                 [ring-cors "0.1.13"]]
  :main youtube-comments.core
  :aot [youtube-comments.core]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})