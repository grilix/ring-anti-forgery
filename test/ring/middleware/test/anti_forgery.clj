(ns ring.middleware.test.anti-forgery
  (:require [ring.middleware.anti-forgery :as af])
  (:use clojure.test
        ring.middleware.anti-forgery
        ring.mock.request))

(deftest forgery-protection-test
  (let [response {:status 200, :headers {}, :body "Foo"}
        handler  (wrap-anti-forgery (constantly response))]
    (are [status req] (= (:status (handler req)) status)
      403 (-> (request :post "/")
              (assoc :form-params {"__anti-forgery-token" "foo"}))
      403 (-> (request :post "/")
              (assoc :session {:__anti-forgery-token "foo"})
              (assoc :form-params {"__anti-forgery-token" "bar"}))
      200 (-> (request :post "/")
              (assoc :session {:__anti-forgery-token "foo"})
              (assoc :form-params {"__anti-forgery-token" "foo"})))))

(deftest request-method-test
  (let [response {:status 200, :headers {}, :body "Foo"}
        handler  (wrap-anti-forgery (constantly response))]
    (are [status req] (= (:status (handler req)) status)
      200 (request :head "/")
      200 (request :get "/")
      403 (request :post "/")
      403 (request :put "/")
      403 (request :patch "/")
      403 (request :delete "/"))))

(deftest csrf-header-test
  (let [response {:status 200, :headers {}, :body "Foo"}
        handler  (wrap-anti-forgery (constantly response))
        sess-req (-> (request :post "/")
                     (assoc :session {:__anti-forgery-token "foo"}))]
    (are [status req] (= (:status (handler req)) status)
      200 (assoc sess-req :headers {"x-csrf-token" "foo"})
      200 (assoc sess-req :headers {"x-xsrf-token" "foo"}))))

(deftest multipart-form-test
  (let [response {:status 200, :headers {}, :body "Foo"}
        handler  (wrap-anti-forgery (constantly response))]
    (is (= (-> (request :post "/")
               (assoc :session {:__anti-forgery-token "foo"})
               (assoc :multipart-params {"__anti-forgery-token" "foo"})
               handler
               :status)
           200))))

(deftest token-in-session-test
  (let [response {:status 200, :headers {}, :body "Foo"}
        handler  (wrap-anti-forgery (constantly response))]
    (is (contains? (:session (handler (request :get "/")))
                   :__anti-forgery-token))
    (is (not= (get-in (handler (request :get "/"))
                      [:session :__anti-forgery-token])
              (get-in (handler (request :get "/"))
                      [:session :__anti-forgery-token])))))

(deftest nil-response-test
  (letfn [(handler [request] nil)]
    (let [response ((wrap-anti-forgery handler) (request :get "/"))]
      (is (nil? response)))))

(deftest no-lf-in-token-test
  (letfn [(handler [request]
            {:status 200
             :headers {}
             :body ""})]
    (let [response ((wrap-anti-forgery handler) (request :get "/"))
          token    (get-in response [:session :__anti-forgery-token])]
      (is (not (.contains token "\n"))))))

(deftest not-overwrite-session-test
  (let [response {:status 200 :headers {} :body nil}
        handler  (wrap-anti-forgery (constantly response))
        session  (:session (handler (-> (request :get "/")
                                        (assoc-in [:session "foo"] "bar"))))]
    (is (contains? session :__anti-forgery-token))
    (is (= (session "foo") "bar"))))

(deftest session-response-test
  (let [response {:status 200 :headers {} :session {"foo" "bar"} :body nil}
        handler  (wrap-anti-forgery (constantly response))
        session  (:session (handler (request :get "/")))]
    (is (contains? session :__anti-forgery-token))
    (is (= (session "foo") "bar"))))

(deftest custom-error-response-test
  (let [response   {:status 200, :headers {}, :body "Foo"}
        error-resp {:status 500, :headers {}, :body "Bar"}
        handler    (wrap-anti-forgery (constantly response)
                                      {:error-response error-resp})]
    (is (= (dissoc (handler (request :get "/")) :session)
           response))
    (is (= (dissoc (handler (request :post "/")) :session)
           error-resp))))

(deftest custom-error-handler-test
  (let [response   {:status 200, :headers {}, :body "Foo"}
        error-resp {:status 500, :headers {}, :body "Bar"}
        handler    (wrap-anti-forgery (constantly response)
                                      {:error-handler (fn [request] error-resp)})]
    (is (= (dissoc (handler (request :get "/")) :session)
           response))
    (is (= (dissoc (handler (request :post "/")) :session)
           error-resp))))

(deftest disallow-both-error-response-and-error-handler
  (is (thrown?
        AssertionError
        (wrap-anti-forgery (constantly {:status 200})
                           {:error-handler (fn [request] {:status 500 :body "Handler"})
                            :error-response {:status 500 :body "Response"}}))))

(deftest custom-read-token-test
  (let [response {:status 200, :headers {}, :body "Foo"}
        handler  (wrap-anti-forgery
                  (constantly response)
                  {:read-token #(get-in % [:headers "x-forgery-token"])})
        req      (-> (request :post "/")
                     (assoc :session {:__anti-forgery-token "foo"})
                     (assoc :headers {"x-forgery-token" "foo"}))]
    (is (= (:status (handler req))
           200))
    (is (= (:status (handler (assoc req :headers {"x-csrf-token" "foo"})))
           403))))

(deftest random-tokens-test
  (let [handler      (fn [req] {:status 200, :headers {}, :body ""})
        get-response (fn [] ((wrap-anti-forgery handler) (request :get "/")))
        responses    (repeatedly 1000 get-response)
        tokens       (->> responses (map :session) (map :__anti-forgery-token))]
    (is (every? #(re-matches #"[A-Za-z0-9+/]{80}" %) tokens))
    (is (= (count tokens) (count (set tokens))))))

(deftest token-available-on-first-request
  (let [handler  (fn [req]
                   (let [token (get-in req [:session :__anti-forgery-token])]
                     {:status 200, :headers {}, :body token}))
        response ((wrap-anti-forgery handler) (request :get "/"))]
    (is (response :body))))
