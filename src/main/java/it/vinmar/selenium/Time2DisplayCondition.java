package it.vinmar.selenium;

import org.openqa.selenium.*;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.*;

import javax.annotation.Nullable;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Time2DisplayCondition implements ExpectedCondition<Long> {

    /**
     * This method generate a "masked" locator based on text matching
     *
     * @param text
     * @return
     */
    public static By MatchContextLocator(final String text) {
        return new By() {
            @Override
            public List<WebElement> findElements(SearchContext searchContext) {

                List<WebElement> resp = null;

                if (searchContext instanceof WebDriver) {
                    WebDriver driver = (WebDriver)searchContext;
                    String source = driver.getPageSource();
                    if (source.contains(text))
                        resp = driver.findElements(By.xpath("//*"));
                }

                return resp;
            }

            @Override
            public WebElement findElement(SearchContext context) {
                return this.findElements(context).get(0);
            }

            @Override
            public String toString() {
                return "By.MatchContext: " + text;
            }
        };
    }


    /**
     *
     */
    private static final Logger logger =  LoggerFactory.getLogger(Time2DisplayCondition.class);

    /**
     * Timeout error
     */
    public final static long TIMEOUT_ERROR__ 	= -1;

    /**
     * Selenium error during page loading
     */
    public final static long SELENIUM_ERROR__ 	= -2;

    /**
     * General error during execution
     */
    public final static long GENERAL_ERROR__ 	= -3;

    /**
     * Javascript error during execution
     */
    public final static long JAVASCRIPT_ERROR__ 	= -4;

    /**
     *
     */
    private class JavascriptException extends RuntimeException {

        /**
		 * 
		 */
		private static final long serialVersionUID = -285881087085066536L;

        /**
         * This runtime exception is proposed to handle when interaction with Java-script engine fails
         *
         * @param message
         */
		public JavascriptException(String message) {
            super(message);
        }
    }

    /**
     *
     *
     *
     */

    private String url2fetch = null;
    private By locator2grab = null;
    private Integer appliedTimeout = null;

    /**
     * hardcode timeout
     */
    private final Integer DEFAULT_TIMEOUT_ = 20;

    public Time2DisplayCondition(String url, By locator, Integer timeout) {

        logger.info("Init the time monitor.");

        url2fetch = url;
        locator2grab = locator;

        if ( timeout != null && timeout > 0 )
            appliedTimeout = timeout;
        else
            appliedTimeout = DEFAULT_TIMEOUT_;

        logger.debug("Url -> " + url2fetch);
        logger.debug("Locator -> " + locator2grab.toString());
        logger.debug("Timeout -> " + appliedTimeout + " sec");
    }

    public Time2DisplayCondition(String url, By locator) {
        this(url, locator, null);
    }

    @Nullable
    @Override
    public Long apply(WebDriver driver) {

        /**
         * General method to execute javascript code on current driver
         */
        Function<String, Object> executeJavascript = (String command) -> {

            Object ret = null;

            try {
                ret = ((JavascriptExecutor) driver).executeScript(command);
            } catch ( Exception e ) {
                throw new JavascriptException(e.getMessage());
            }

            logger.debug("Javascript code \""+command+"\" returns \"" + ret.toString() + "\"");

            return ret;
        };

        /**
         * It returns the total time in millis between events (navigationStart,domComplete)
         */
        Supplier<Long> fetchTotalLoadTime = () -> {
            return (Long)executeJavascript.apply("return (performance.timing.domComplete - performance.timing.navigationStart)");
        };

        /**
         * It returns the total time in millis between events (navigationStart,now)
         */
        Supplier<Long> fetchCurrentLoadTime = () -> {
            return (Long)executeJavascript.apply("return (Date.now() - performance.timing.navigationStart)");
        };

        /**
         * It returns the "readyState" field
         */
        Supplier<String> fetchReadyState = () -> {
            return (String)executeJavascript.apply("return document.readyState");
        };

        Long result = -999L;

        /**
         * This action describes if loading url complete with success for Selenium
         */
        final CompletableFuture<WebDriver> loadingUrl = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
                try {

                    logger.debug("Starting fetching url");

                    // embody it into a Future that fail on exception
                    driver.get(url2fetch);

                    loadingUrl.complete(driver);

                } catch (TimeoutException ex) {
                    loadingUrl.completeExceptionally(ex);
                }
        });

		

        /**
         * Waiting for DOM complete loading
         */
        Future<String> completeReadyState = ForkJoinPool.commonPool().submit( () -> {

            String domStatus = "";

            do {
                Sleeper.SYSTEM_SLEEPER.sleep(new Duration( 1, TimeUnit.SECONDS));

                Long loadTime = fetchCurrentLoadTime.get();
                domStatus = fetchReadyState.get();

                if (loadTime >= appliedTimeout * 1000) {
                    String message = "Timeout occurs before \"DOM Ready\" state!";
                    logger.debug(message);
                    throw new TimeoutException(message);
                } else {
                    logger.debug("DOM complete loading in " + fetchTotalLoadTime.get() + " ms");
                }

                logger.debug("After " + loadTime + " ms DOM state is " + domStatus);

            } while (!domStatus.equals("complete"));

            return "complete";
        });		
		
		
		
        /**
         * After loadingUrl completes and completeReadyState, it checks starts the real rule checking
         */
        Future<Boolean> resultIsPresence = ForkJoinPool.commonPool().submit( () -> {

                WebDriverWait driverWait = new WebDriverWait(loadingUrl.get(), appliedTimeout);

                logger.debug("Starting monitoring on locator.");

                WebElement find = driverWait.until(ExpectedConditions.presenceOfElementLocated(locator2grab));

                return find != null;
        });

        try {

            logger.trace("Component is present " + resultIsPresence.get());

            if ( completeReadyState.get().equals("complete") ) {

                if ( resultIsPresence.get() ) {
                    Long loadTime = fetchTotalLoadTime.get();

                    if (loadTime >= appliedTimeout * 1000) {
                        result = TIMEOUT_ERROR__;
                        logger.debug("Ending monitoring on locator with timeout!");
                    } else {
                        result = loadTime;
                        logger.debug("Ending monitoring on locator with time " + result + " ms");
                    }
                }
                else
                    result = SELENIUM_ERROR__;
            }
            else {
                result = JAVASCRIPT_ERROR__;
                logger.error("Ending monitoring on locator with \"Javascript ERROR\"!");
            }

        } catch ( TimeoutException tex ) {
            result = TIMEOUT_ERROR__;
            logger.error("Ending monitoring on locator with \"Timeout\"!", tex);
        }
        catch (InterruptedException | ExecutionException ee) {

            String message = "";

            if (ee.getCause() instanceof TimeoutException){
                result = TIMEOUT_ERROR__;
                message = "Ending monitoring on locator with \"Timeout\"!";
            }
            else {
                result = GENERAL_ERROR__;
                message = "Ending monitoring on locator with \"General ERROR\"!";
            }

            logger.error( message, ee);
        }
        catch ( JavascriptException jex ) {
            result = JAVASCRIPT_ERROR__;
            logger.error("Ending monitoring on locator with \"Javascript ERROR\"!", jex);
        }
        catch ( Exception ex ) {
            logger.error("Monitor crashes with unexpected error!", ex);
        }

        return result;
    }

}
