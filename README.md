#### The Task
Develop a webservice using Scala which accepts an integer value and upon the first request, returns whether the number is prime or not. When this request is received, another task should be kicked off which calculates and stores all prime numbers less than the number received. After these numbers are calculated, subsequent requests with the original number should return both whether the number is prime or not and a list of all primes less than that number.

Example:

```
GET: /primes/5
-> { isPrime: true, primes:[]}

GET: /prime/5
-> { isPrime: true, primes: [2,3] }

GET: /prime/8
-> { isPrime: false, primes: [] }

GET: /prime/8
-> { isPrime: false, primes: [2,3,5,7] }
```

#### Design
The akka-http service get a request with an integer and send ask message to PrimeCach actor.  
The PrimeCache actor check if all the primes up to that integer are in the cache.  
If yes then asks PrimeStream for the data, if not, PrimeCache asks PrimeStream to check if number is a prime and sends PrimeStream to perform the task in the background.  
Upon receiving the message, the PrimeStream lazily computes the primes up to the given integer then send largest integer checked so far to PrimeCache.  

#### Usage
At the project root:  
`./gradlew runJar`  
Use curl or browser or Postman to test at [http://localhost:8080/primes/5](http://localhost:8080/primes/5)

To run the test:  
`./gradlew test`

