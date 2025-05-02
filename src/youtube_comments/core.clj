(ns youtube-comments.core
  (:require [org.httpkit.server :refer [run-server]]
            [clj-http.client :as client]
            [ring.util.response :refer [response content-type header]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:gen-class))

;; Use environment variable for API key
(def api-key (or (System/getenv "YOUTUBE_API_KEY") ""))

;; Função para verificar se a API key está configurada
(defn validate-api-key []
  (when (str/blank? api-key)
    (println "ALERTA: API key não configurada! Configure a variável de ambiente YOUTUBE_API_KEY.")
    (println "Exemplo: export YOUTUBE_API_KEY=sua_chave_api_aqui")))

;; Definição do limite máximo de comentários
(def max-comments 5000)

(defn fetch-comments-page
  "Busca uma página de comentários para o vídeo especificado"
  [video-id page-token]
  (println (str "Buscando página " (if page-token (str "com token: " page-token) "inicial") " para o vídeo ID: " video-id))
  (if (str/blank? api-key)
    (do
      (println "ERRO: Não é possível buscar comentários sem uma API key configurada.")
      {:comments [] :next-token nil})
    (try
      (let [url "https://www.googleapis.com/youtube/v3/commentThreads"
            params {:query-params (merge
                                    {:key api-key
                                     :part "snippet"
                                     :videoId video-id
                                     :maxResults 100}
                                    (when page-token
                                      {:pageToken page-token}))
                    :as :stream}
            response (client/get url params)
            body (json/read-str (slurp (:body response)) :key-fn keyword)
            items (get body :items)
            next-page-token (get body :nextPageToken)]
        (println "Página recebida. Status:" (:status response) 
                 "Comentários nesta página:" (count items)
                 "Próximo token:" (or next-page-token "nenhum"))
        {:comments (->> items
                        (map #(get-in % [:snippet :topLevelComment :snippet :textDisplay]))
                        (filter some?))
         :next-token next-page-token})
      (catch Exception e
        (println "Erro ao buscar página de comentários:" (.getMessage e))
        (.printStackTrace e)
        {:comments [] :next-token nil}))))

(defn fetch-comments 
  "Busca comentários para o vídeo especificado, com paginação até o limite definido"
  [video-id]
  (println "Iniciando busca de comentários (até" max-comments "comentários) para o vídeo ID:" video-id)
  (loop [all-comments []
         next-token nil]
    (if (and 
          ;; Ainda temos páginas para buscar
          (or (nil? next-token) (not (str/blank? next-token)))
          ;; Ainda não atingimos o limite de comentários
          (< (count all-comments) max-comments))
      ;; Buscar próxima página
      (let [{:keys [comments next-token]} (fetch-comments-page video-id next-token)
            new-comments (concat all-comments comments)
            total-count (count new-comments)]
        (println "Total de comentários acumulados:" total-count)
        (if (or (nil? next-token) (>= total-count max-comments))
          ;; Retornar se não há mais páginas ou atingimos o limite
          (take max-comments new-comments)
          ;; Continuar buscando mais páginas
          (recur new-comments next-token)))
      ;; Retornar os comentários acumulados
      all-comments)))

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
    (cond
      ;; Rota para verificar o status da API
      (= uri "/status")
      (-> (response (if (str/blank? api-key)
                      "API Key não configurada. Configure a variável de ambiente YOUTUBE_API_KEY."
                      "Serviço operacional. API Key configurada corretamente."))
          (content-type "text/plain; charset=utf-8"))
      
      ;; Rota para buscar comentários
      (= uri "/comments")
      (let [video-id (get params "video_id")
            keyword (get params "keyword")
            limit (try
                    (Integer/parseInt (or (get params "limit") (str max-comments)))
                    (catch Exception _ max-comments))]
        (println "Processando requisição para video_id:" video-id "keyword:" keyword "limit:" limit)
        (cond
          ;; Verifica se a API key está configurada
          (str/blank? api-key)
          (-> (response "ERRO: API Key não configurada. O administrador do serviço precisa configurar a variável de ambiente YOUTUBE_API_KEY.")
              (content-type "text/plain; charset=utf-8"))
          
          ;; Verifica se o video_id foi fornecido
          (nil? video-id)
          (-> (response "Parâmetro video_id é obrigatório")
              (content-type "text/plain; charset=utf-8"))
          
          ;; Processa a requisição normalmente
          :else
          (let [comments (fetch-comments video-id)
                _ (println "Comentários obtidos:" (count comments))
                filtered (filter-comments comments keyword)
                limited (take limit filtered)]
            (println "Total de comentários encontrados:" (count comments))
            (println "Total de comentários filtrados:" (count filtered))
            (println "Total de comentários retornados:" (count limited))
            (if (seq limited)
              (-> (response (str/join "\n" limited))
                  (content-type "text/plain; charset=utf-8")
                  (header "Content-Disposition" "attachment; filename=comments.txt"))
              (-> (response "Nenhum comentário encontrado com os critérios.")
                  (content-type "text/plain; charset=utf-8"))))))
      
      ;; Rota não encontrada
      :else
      (-> (response "Rotas disponíveis:\n/comments?video_id=ID[&keyword=PALAVRA][&limit=NUMERO]\n/status")
          (content-type "text/plain; charset=utf-8")))))

(defn -main []
  (println "Iniciando servidor na porta 8080")
  (println "Limite máximo de comentários definido para:" max-comments)
  
  ;; Validar a API key
  (validate-api-key)
  
  (println "Status da API key:" (if (str/blank? api-key) 
                                "NÃO CONFIGURADA! O serviço não funcionará corretamente." 
                                "Configurada corretamente."))
  
  (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 8080)
        server (run-server
                (-> handler
                    wrap-params
                    (wrap-cors :access-control-allow-origin [#".*"]
                               :access-control-allow-methods [:get]))
                {:port port})]
    ;; Manter o processo principal em execução
    (println "Servidor iniciado com sucesso na porta" port "! Pressione Ctrl+C para parar.")
    (println "Exemplo de uso: curl \"http://localhost:8080/comments?video_id=VIDEO_ID&limit=500\"")
    @(promise)))
