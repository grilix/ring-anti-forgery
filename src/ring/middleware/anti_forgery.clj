(ns ring.middleware.anti-forgery
  "Ring middleware to prevent CSRF attacks with an anti-forgery token."
  (:require [crypto.random :as random]
            [crypto.equality :as crypto]))

(defn- new-token []
  (random/base64 60))

(defn- session-token [request]
  (get-in request [:session :__anti-forgery-token]))

(defn- prepare-request [request]
  (if (session-token request)
    request
    (assoc-in request
              [:session :__anti-forgery-token]
              (new-token))))

(defn- assoc-session-token [response request]
  (let [request-token (session-token request)
        response-token (session-token response)]
    (if (= request-token response-token)
      response
      (-> response
          (assoc :session (:session response (:session request)))
          (assoc-in [:session :__anti-forgery-token] request-token)))))

(defn- form-params [request]
  (merge (:form-params request)
         (:multipart-params request)))

(defn- default-request-token [request]
  (or (-> request form-params (get "__anti-forgery-token"))
      (-> request :headers (get "x-csrf-token"))
      (-> request :headers (get "x-xsrf-token"))))

(defn- valid-request? [request read-token]
  (let [user-token   (read-token request)
        stored-token (session-token request)]
    (and user-token
         stored-token
         (crypto/eq? user-token stored-token))))

(defn- get-request? [{method :request-method}]
  (or (= method :head)
      (= method :get)))

(defn- access-denied [body]
  {:status  403
   :headers {"Content-Type" "text/html"}
   :body    body})

(defn- handle-error [options request]
  (let [default-response (access-denied "<h1>Invalid anti-forgery token</h1>")
        error-response   (:error-response options default-response)
        error-handler    (:error-handler options (constantly error-response))]
    (error-handler request)))

(defn wrap-anti-forgery
  "Middleware that prevents CSRF attacks. Any POST request to the handler
  returned by this function must contain a valid anti-forgery token, or else an
  access-denied response is returned.

  The anti-forgery token can be placed into a HTML page using the session key
  __anti-forgery-token, which is a random key unique to the
  current session. By default, the token is expected to be in a form field
  named '__anti-forgery-token', or in the 'X-CSRF-Token' or 'X-XSRF-Token'
  headers.

  Accepts the following options:

  :read-token     - a function that takes a request and returns an anti-forgery
                    token, or nil if the token does not exist

  :error-response - the response to return if the anti-forgery token is
                    incorrect or missing

  :error-handler  - a handler function to call if the anti-forgery token is
                    incorrect or missing.

  Only one of :error-response, :error-handler may be specified."
  {:arglists '([handler] [handler options])}
  [handler & [{:keys [read-token]
               :or   {read-token default-request-token}
               :as   options}]]
  {:pre [(not (and (:error-response options)
                   (:error-handler options)))]}
  (fn [request]
    (let [request (prepare-request request)]
      (if (and (not (get-request? request))
               (not (valid-request? request read-token)))
        (handle-error options request)
        (if-let [response (handler request)]
          (assoc-session-token response request))))))

