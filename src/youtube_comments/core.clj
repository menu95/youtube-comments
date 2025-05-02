(ns youtube-comments.core
  (:require [org.httpkit.server :refer [run-server]]
            [clj-http.client :as client]
            [ring.util.response :refer [response content-type header]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.string :as str]
            [clojure.data.json :as json]) ; Certifique-se de incluir esta dependência
  (:gen-class))

;; Use environment variable with fallback to ensure API key is available
(def api-key (System/getenv "YOUTUBE_API_KEY")
  )

(defn fetch-comments [video-id]
  (println "Buscando comentários para o vídeo ID:" video-id)
  (try
    (let [url "https://www.googleapis.com/youtube/v3/commentThreads"
          params {:query-params {:key api-key
                                 :part "snippet"
                                 :videoId video-id
                                 :maxResults 100}
                  :as :stream} ; Mudamos para stream em vez de :json
          response (client/get url params)
          body (json/read-str (slurp (:body response)) :key-fn keyword) ; Parse manual do JSON
          items (get body :items)]
      (println "Resposta da API recebida. Status:" (:status response))
      (->> items
           (map #(get-in % [:snippet :topLevelComment :snippet :textDisplay]))
           (filter some?)))
    (catch Exception e
      (println "Erro ao buscar comentários:" (.getMessage e))
      (.printStackTrace e)
      [])))

(defn filter-comments [comments keyword]
  (println (if (or (nil? keyword) (str/blank? keyword))
             "Nenhuma palavra-chave fornecida. Retornando todos os comentários."
             (str "Filtrando comentários pela palavra-chave: " keyword)))
  (if (or (nil? keyword) (str/blank? keyword))
    comments
    (filter #(str/includes? (str/lower-case (or % "")) (str/lower-case keyword)) comments)))

(defn handler [req]
  (let [uri (:uri req)
        params (:params req)]
    (println "Requisição recebida. URI:" uri)
    (println "Params:" params)
    (if (= uri "/comments")
      (let [video-id (get params "video_id")
            keyword  (get params "keyword")]
        (println "Processando requisição para video_id:" video-id "keyword:" keyword)
        (if (nil? video-id)
          (-> (response "Parâmetro video_id é obrigatório")
              (content-type "text/plain; charset=utf-8"))
          (let [comments (fetch-comments video-id)
                _ (println "Comentários obtidos:" (count comments))
                filtered (filter-comments comments keyword)]
            (println "Total de comentários encontrados:" (count comments))
            (println "Total de comentários filtrados:" (count filtered))
            (if (seq filtered)
              (-> (response (str/join "\n" filtered))
                  (content-type "text/plain; charset=utf-8")
                  (header "Content-Disposition" "attachment; filename=comments.txt"))
              (-> (response "Nenhum comentário encontrado com os critérios.")
                  (content-type "text/plain; charset=utf-8"))))))
      (-> (response "Rota não encontrada. Use /comments?video_id=ID")
          (content-type "text/plain; charset=utf-8")))))

(defn -main []
  (println "Iniciando servidor na porta 8080")
  (println "Usando API key:" (if (str/blank? api-key)
                               "Nenhuma chave definida!"
                               (str (subs api-key 0 5) "...")))
  (let [server (run-server
                 (-> handler
                     wrap-params
                     (wrap-cors :access-control-allow-origin [#".*"]
                                :access-control-allow-methods [:get]))
                 {:port 8080})]
    ;; Manter o processo principal em execução
    (println "Servidor iniciado com sucesso! Pressione Ctrl+C para parar.")
    (println "Exemplo de uso: curl \"http://localhost:8080/comments?video_id=PIQpHVXjAt4\"")
    @(promise)))
