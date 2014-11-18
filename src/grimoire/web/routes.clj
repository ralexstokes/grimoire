(ns grimoire.web.routes
  (:require [compojure.core :refer [defroutes context GET let-routes]]
            [compojure.route :as route]
            [grimoire.util :as util]
            [grimoire.things :as thing]
            [grimoire.web.views :as v ]
            [grimoire.web.views.errors :as v.e]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre :refer [info warn]]))

;; FIXME: Do I want to try and build "things" here in the routing
;; rather than parsing back and forth using the string path
;; representation? Probably should..

(defroutes store
  (context ["/store"] []
    (GET "/" {uri :uri}
      (info (pr-str {:uri uri :type :text}))
      (v/api-page))

    (context ["/:groupid"] [groupid]
      (let-routes [t (thing/->Group groupid)]
          (GET "/" {uri :uri}
            (info (pr-str {:uri uri :type :text}))
            (v/groupid-page t))

        (context ["/:artifactid"] [artifactid]
          (let-routes [t (thing/->T :artifact t artifactid)]
              (GET "/" {uri :uri}
                (info (pr-str {:uri uri :type :text}))
                (v/artifactid-page t))

            (context ["/:version", :version #"[0-9]+.[0-9]+.[0-9]+"] [version]
              (let-routes [t (thing/->T :version t version)]
                  (GET "/" {uri :uri}
                    (info (pr-str {:uri uri :type :text}))
                    (v/version-page t))

                (context "/:namespace" [namespace]
                  (let-routes [t (thing/->T :namespace t namespace)]
                      (GET "/" {uri :uri}
                        (info (pr-str {:uri uri :type :text}))
                        (v/namespace-page-memo t))

                    (context "/:symbol" [symbol]
                      (let-routes [t (thing/->T :def t symbol)]
                          (GET "/" {header-type :content-type
                                    {param-type :type} :params
                                    :as req
                                    uri :uri}
                            (let [type    (or header-type param-type :html)
                                  symbol' (util/update-munge symbol)]
                              (cond
                               ;; FIXME this is a bit of a hack to handle catch/finally
                               (#{"catch" "finally"} symbol)
                               ,,(response/redirect
                                  (format "/%s/%s/%s/%s/%s/"
                                          groupid artifactid version
                                          namespace"try"))

                               ;; handle the case of redirecting due to munging
                               (not (= symbol symbol'))
                               ,,(response/redirect
                                  (format "/%s/%s/%s/%s/%s/"
                                          groupid artifactid version
                                          namespace symbol'))

                               :else
                               ,,(let [res (v/symbol-page type t)]
                                   (info (pr-str {:uri uri :type type}))
                                   res))))

                        (route/not-found
                         (fn [{uri :uri
                              header-type :content-type
                              {param-type :type} :params}]
                           (let [type (or header-type param-type :text/html)]
                             (warn (pr-str {:uri uri}))
                             (v.e/error-unknown-symbol type t))))))

                    (route/not-found
                     (fn [{uri :uri}]
                       (warn (pr-str {:uri uri}))
                       (v.e/error-unknown-namespace type t)))))

                (route/not-found
                 (fn [{uri :uri}]
                   (warn (pr-str {:uri uri}))
                   (v.e/error-unknown-version t)))))

            (route/not-found
             (fn [{uri :uri}]
               (warn (pr-str {:uri uri}))
               (v.e/error-unknown-artifact t)))))

        (route/not-found
         (fn [{uri :uri}]
           (warn (pr-str {:uri uri}))
           (v.e/error-unknown-group t)))))))

;; FIXME: Implement rather than stub

(defroutes api-v0
  (context ["/api/v0"] []
    (GET "/" {{op :op} :params}
      ;; op ∈ #{"groups"}
      )

    (context ["/:group"] [group]
      (GET "/" {{op :op} :params}
        ;; op ∈ #{"artifacts" "notes"}
        )

      (context ["/:artifact"] [artifact]
        (GET "/" {{op :op} :params}
          ;; op ∈ #{"versions" "notes" "url"}
          )

        (context ["/:version"] [version]
          (GET "/" {{op :op} :params}
            ;; op ∈ #{"namespaces" "notes"}
            )

          (context ["/:namespace"] [namespace]
            (GET "/" {{op :op} :params}
              ;; op ∈ #{"notes"
              ;;        "docs"
              ;;        "symbols"
              ;;        "vars"
              ;;        "fns"
              ;;        "types"
              ;;        "added"}
              )

            (context ["/:symbol"] [symbol]
              (GET "/" {{op :op} :params}
                ;; op ∈ #{"notes"
                ;;        "type"
                ;;        "added"
                ;;        "doc"
                ;;        "file"
                ;;        "line"
                ;;        "column"}
                ))))))))

(defroutes articles
  (context ["/articles"] []
    (GET "/:id" {{id :id} :params uri :uri}
      (when-let [res (v/markdown-page (str "articles/" id))]
        (info (pr-str {:uri uri :type :html}))
        res))

    (GET "/" {uri :uri}
      (info (pr-str {:uri uri :type :html}))
      (v/articles-list))

    (route/not-found
     (fn [{uri :uri}]
       (warn (pr-str {:uri uri}))
       (v.e/error-404)))))

(defroutes app
  (GET "/" {uri :uri}
    (info (pr-str {:uri uri :type :html}))
    (v/home-page))

  (GET "/favicon.ico" []
    (response/redirect "/public/favicon.ico"))

  (GET "/robots.txt" []
    (response/redirect "/public/robots.txt"))

  (route/resources "/public")

  ;; The main browsing interface
  store

  ;; The article store
  articles

  ;; The v0 API
  api-v0

  ;; Redirect legacy paths into the store
  (context ["/:version", :version #"[0-9]+.[0-9]+.[0-9]+"] [version]
    (fn [request]
      (warn "Redirecting!")
      (response/redirect (str "/store/org.clojure/clojure"
                              (:uri request)))))

  (route/not-found
   (fn [{uri :uri}]
     (warn (pr-str {:uri uri}))
     (v.e/error-404))))
