import jnative.webui.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        WebUI.mount("#app", new CounterPage());
    }
}

class CounterPage extends WebUiComponent {
    IntState count;

    CounterPage() {
        count = WebUI.stateInt(0);
    }

    public View render() {
        return Html.div2(
            Html.h1Dynamic(new CountLabel(count)),
            Html.buttonLabel(
                "Increment",
                Html.onClickVoid(new IncrementAction(count))
            )
        );
    }
}

class CountLabel implements StringProducer {
    IntState count;

    CountLabel(IntState count) {
        this.count = count;
    }

    public String produce() {
        return "Count: " + String.valueOf(count.get());
    }
}

class IncrementAction implements VoidCallback {
    IntState count;

    IncrementAction(IntState count) {
        this.count = count;
    }

    public void run() {
        count.set(count.get() + 1);
    }
}
